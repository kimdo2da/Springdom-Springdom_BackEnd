package com.example.lightsafe.post;

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import com.example.lightsafe.user.UserService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.exception.ForbiddenException;
import com.example.lightsafe.common.exception.NotFoundException;
import com.example.lightsafe.common.exception.UnauthorizedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostAttachmentRepository postAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final UserService userService;

    private static final String CATEGORY_NOTICE =
            "NOTICE";

    private static final String DEFAULT_POST_CATEGORY =
            "INFO";

    private static final Set<String> USER_POST_CATEGORIES =
            Set.of(
                    "INFO",
                    "QUESTION",
                    "REPORT",
                    "TIP"
            );

    private static final Set<String> READABLE_CATEGORIES =
            Set.of(
                    "NOTICE",
                    "INFO",
                    "QUESTION",
                    "REPORT",
                    "TIP"
            );

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            PostLikeRepository postLikeRepository,
            PostAttachmentRepository postAttachmentRepository,
            FileStorageService fileStorageService,
            UserRepository userRepository,
            UserService userService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.postAttachmentRepository = postAttachmentRepository;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    // =========================================================
    // 1) 커뮤니티 목록 (공지 3개 상단 고정 + 일반글 페이지네이션)
    // =========================================================
    @Transactional(readOnly = true)
    public CommunityPostsResponse getCommunity(
            int page,
            int size,
            String sort
    ) {
        int safePage =
                Math.max(
                        page,
                        0
                );

        int safeSize =
                size <= 0
                        ? 10
                        : size;

        List<PostListResponse> notices =
                postRepository
                        .findTop3ByIsNoticeTrueOrderByCreatedAtDesc()
                        .stream()
                        .map(PostListResponse::from)
                        .toList();

        Sort sortSpec =
                buildSearchSort(
                        sort
                );

        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize,
                        sortSpec
                );

        Page<Post> postPage =
                postRepository.findByIsNoticeFalse(
                        pageable
                );

        List<PostListResponse> items =
                postPage.getContent()
                        .stream()
                        .map(PostListResponse::from)
                        .toList();

        PostPageInfo pageInfo =
                new PostPageInfo(
                        postPage.getNumber(),
                        postPage.getSize(),
                        postPage.getTotalElements(),
                        postPage.getTotalPages()
                );

        return new CommunityPostsResponse(
                notices,
                items,
                pageInfo
        );
    }

    @Transactional(readOnly = true)
    public CommunitySummaryResponse getCommunitySummary() {
        List<PostListResponse> notices = postRepository
                .findTop5ByCategoryOrderByCreatedAtDesc("NOTICE")
                .stream()
                .map(PostListResponse::from)
                .toList();

        List<PostListResponse> questions = postRepository
                .findTop5ByCategoryOrderByCreatedAtDesc("QUESTION")
                .stream()
                .map(PostListResponse::from)
                .toList();

        List<PostListResponse> info = postRepository
                .findTop5ByCategoryOrderByCreatedAtDesc("INFO")
                .stream()
                .map(PostListResponse::from)
                .toList();

        return new CommunitySummaryResponse(notices, questions, info);
    }

    @Transactional(readOnly = true)
    public PostListPageResponse getPostsByCategory(
            String category,
            int page,
            int size,
            String sort
    ) {
        String normalizedCategory =
                normalizeReadableCategory(
                        category
                );

        int safePage =
                Math.max(
                        page,
                        0
                );

        int safeSize =
                size <= 0
                        ? 10
                        : size;

        Sort sortSpec =
                buildSearchSort(
                        sort
                );

        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize,
                        sortSpec
                );

        Page<Post> postPage =
                postRepository.findByCategory(
                        normalizedCategory,
                        pageable
                );

        List<PostListResponse> items =
                postPage.getContent()
                        .stream()
                        .map(PostListResponse::from)
                        .toList();

        PostPageInfo pageInfo =
                new PostPageInfo(
                        postPage.getNumber(),
                        postPage.getSize(),
                        postPage.getTotalElements(),
                        postPage.getTotalPages()
                );

        return new PostListPageResponse(
                items,
                pageInfo
        );
    }

    @Transactional(readOnly = true)
    public PostListPageResponse searchPosts(String keyword, int page, int size, String sort) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BadRequestException("검색어를 입력해주세요.");
        }

        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? 10 : size;
        Sort sortSpec = buildSearchSort(sort);

        Pageable pageable = PageRequest.of(safePage, safeSize, sortSpec);
        Page<Post> postPage = postRepository.findByTitleContainingIgnoreCase(keyword.trim(), pageable);

        List<PostListResponse> items = postPage.getContent()
                .stream()
                .map(PostListResponse::from)
                .toList();

        PostPageInfo pageInfo = new PostPageInfo(
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages()
        );

        return new PostListPageResponse(items, pageInfo);
    }

    // =========================================================
    // 2) 게시글 상세 (+댓글 트리)
    // =========================================================
    @Transactional(readOnly = true)
    public PostDetailResponse getPostDetail(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시글입니다. id=" + postId));

        User currentUser = userService.getCurrentUser();
        boolean isLiked = false;
        if (currentUser != null) {
            isLiked = postLikeRepository.existsByPostPostIdAndUserUserId(postId, currentUser.getUserId());
        }

        List<Comment> comments = commentRepository.findByPostPostIdOrderByCreatedAtAsc(postId);
        List<CommentResponse> commentTree = buildCommentTree(comments);

        List<AttachmentResponse> attachments = postAttachmentRepository
                .findByPostPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(AttachmentResponse::from)
                .toList();

        return new PostDetailResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getUser().getUserId(),
                post.getUser().getNickname(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                isLiked,
                post.getCreatedAt(),
                commentTree,
                attachments
        );
    }

    @Transactional
    public void increaseViewCount(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시글입니다. id=" + postId));
        Integer vc = post.getViewCount();
        post.setViewCount((vc == null ? 0 : vc) + 1);
    }

    // ✅ 안전한 트리 빌드 (record 직접 add 금지)
    // ✅ 순서 보장 트리 빌드: comments(이미 createdAt ASC) 순서대로 연결
    private List<CommentResponse> buildCommentTree(List<Comment> comments) {
        Map<Long, CommentNode> map = new HashMap<>();

        // 1) 모든 댓글을 노드로 변환해서 map에 저장
        for (Comment c : comments) {
            Long parentId = (c.getParent() == null) ? null : c.getParent().getCommentId();
            map.put(c.getCommentId(), new CommentNode(
                    c.getCommentId(),
                    c.getUser().getUserId(),
                    c.getUser().getNickname(),
                    c.getContent(),
                    parentId,
                    c.getCreatedAt()
            ));
        }

        // 2) comments 순서대로(작성순) 부모-자식 연결
        List<CommentNode> roots = new ArrayList<>();
        for (Comment c : comments) {
            CommentNode node = map.get(c.getCommentId());   // ✅ 여기서 node를 꺼내야 함
            if (node == null) continue;

            if (node.parentId == null) {
                roots.add(node);
            } else {
                CommentNode parent = map.get(node.parentId);
                if (parent != null) parent.replies.add(node);
                else roots.add(node); // parent 누락 시 루트 처리
            }
        }

        // 3) roots는 이미 작성순인데 혹시 몰라 안전 정렬
        roots.sort(Comparator.comparing(n -> n.createdAt));
        return roots.stream().map(CommentNode::toRecord).toList();
    }


    private static class CommentNode {
        Long commentId;
        Long userId;
        String nickname;
        String content;
        Long parentId;
        LocalDateTime createdAt;
        List<CommentNode> replies = new ArrayList<>();

        CommentNode(Long commentId, Long userId, String nickname, String content, Long parentId, LocalDateTime createdAt) {
            this.commentId = commentId;
            this.userId = userId;
            this.nickname = nickname;
            this.content = content;
            this.parentId = parentId;
            this.createdAt = createdAt;
        }

        CommentResponse toRecord() {
            replies.sort(Comparator.comparing(n -> n.createdAt));
            return new CommentResponse(
                    commentId, userId, nickname, content, parentId, createdAt,
                    replies.stream().map(CommentNode::toRecord).toList()
            );
        }
    }

    // =========================================================
    // 3) 댓글/대댓글 CRUD
    // =========================================================
    @Transactional
    public Long createComment(Long postId, CommentCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시글입니다. id=" + postId));

        User user = userService.getCurrentUser();
        if (user == null) throw new UnauthorizedException("로그인이 필요합니다.");

        Comment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new NotFoundException("존재하지 않는 부모 댓글입니다. id=" + request.parentId()));

            if (!Objects.equals(parent.getPost().getPostId(), postId)) {
                throw new BadRequestException("부모 댓글이 해당 게시글에 속하지 않습니다.");
            }
        }

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setUser(user);
        comment.setParent(parent);
        comment.setContent(request.content());

        Comment saved = commentRepository.save(comment);

        Integer cc = post.getCommentCount();
        post.setCommentCount((cc == null ? 0 : cc) + 1);

        return saved.getCommentId();
    }

    @Transactional
    public void updateComment(
            Long postId,
            Long commentId,
            CommentUpdateRequest request
    ) {
        postRepository.findById(postId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 게시글입니다. id=" + postId
                        )
                );

        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 댓글입니다. id=" + commentId
                        )
                );

        if (!Objects.equals(
                comment.getPost().getPostId(),
                postId
        )) {
            throw new BadRequestException(
                    "해당 게시글의 댓글이 아닙니다."
            );
        }

        User user =
                userService.getCurrentUser();

        if (user == null
                || !Objects.equals(
                comment.getUser().getUserId(),
                user.getUserId()
        )) {
            throw new ForbiddenException(
                    "본인의 댓글만 수정할 수 있습니다."
            );
        }

        comment.setContent(
                request.content().trim()
        );
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시글입니다. id=" + postId));

        Comment target = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 댓글입니다. id=" + commentId));

        User user =
                userService.getCurrentUser();

        if (!isOwnerOrAdmin(
                user,
                target.getUser()
        )) {
            throw new ForbiddenException(
                    "댓글을 삭제할 권한이 없습니다."
            );
        }

        if (!Objects.equals(target.getPost().getPostId(), postId)) {
            throw new BadRequestException("해당 게시글의 댓글이 아닙니다.");
        }

        List<Long> deleteIds = collectDescendantIdsInclusive(postId, commentId);
        Collections.reverse(deleteIds);
        commentRepository.deleteAllById(deleteIds);

        Integer cc = post.getCommentCount();
        int safeCc = (cc == null) ? 0 : cc;
        post.setCommentCount(Math.max(0, safeCc - deleteIds.size()));
    }

    private List<Long> collectDescendantIdsInclusive(Long postId, Long rootCommentId) {
        List<Comment> flat = commentRepository.findByPostPostIdOrderByCreatedAtAsc(postId);

        Map<Long, List<Long>> children = new HashMap<>();
        for (Comment c : flat) {
            Long pid = (c.getParent() == null) ? null : c.getParent().getCommentId();
            if (pid != null) {
                children.computeIfAbsent(pid, k -> new ArrayList<>()).add(c.getCommentId());
            }
        }

        List<Long> ids = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootCommentId);

        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            ids.add(cur);

            List<Long> kids = children.get(cur);
            if (kids != null) for (Long k : kids) stack.push(k);
        }
        return ids;
    }

    private Sort buildSearchSort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String key = sort.trim().toLowerCase();
        return switch (key) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "views" -> Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "likes" -> Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "comments" -> Sort.by(Sort.Direction.DESC, "commentCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "latest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
    private String normalizeReadableCategory(
            String category
    ) {
        if (category == null
                || category.isBlank()) {

            throw new BadRequestException(
                    "카테고리를 입력해주세요."
            );
        }

        String normalized =
                category
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!READABLE_CATEGORIES.contains(
                normalized
        )) {
            throw new BadRequestException(
                    "허용되지 않은 게시글 카테고리입니다. 허용값: NOTICE, INFO, QUESTION, REPORT, TIP"
            );
        }

        return normalized;
    }


    private String normalizeCreateCategory(
            String category
    ) {
        if (category == null
                || category.isBlank()) {

            return DEFAULT_POST_CATEGORY;
        }

        return normalizeUserPostCategory(
                category
        );
    }


    private String normalizeUpdateCategory(
            String category
    ) {
        if (category == null
                || category.isBlank()) {

            throw new BadRequestException(
                    "카테고리를 입력해주세요."
            );
        }

        return normalizeUserPostCategory(
                category
        );
    }


    private String normalizeUserPostCategory(
            String category
    ) {
        String normalized =
                category
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (CATEGORY_NOTICE.equals(
                normalized
        )) {
            throw new BadRequestException(
                    "일반 게시글은 NOTICE 카테고리를 사용할 수 없습니다."
            );
        }

        if (!USER_POST_CATEGORIES.contains(
                normalized
        )) {
            throw new BadRequestException(
                    "허용되지 않은 게시글 카테고리입니다. 허용값: INFO, QUESTION, REPORT, TIP"
            );
        }

        return normalized;
    }


    // =========================================================
    // 5) 좋아요 / 좋아요 취소
    // =========================================================
    @Transactional
    public void likePost(Long postId) {
        User user = userService.getCurrentUser();
        if (user == null) throw new UnauthorizedException("로그인이 필요합니다.");

        if (postLikeRepository.existsByPostPostIdAndUserUserId(postId, user.getUserId())) return;

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시글입니다. id=" + postId));

        PostLike like = new PostLike();
        like.setPost(post);
        like.setUser(user);
        postLikeRepository.save(like);

        Integer lc = post.getLikeCount();
        post.setLikeCount((lc == null ? 0 : lc) + 1);
    }

    @Transactional
    public void unlikePost(Long postId) {
        User user =
                userService.getCurrentUser();

        if (user == null) {
            throw new UnauthorizedException(
                    "로그인이 필요합니다."
            );
        }

        Post post =
                postRepository.findById(
                                postId
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 게시글입니다. id=" + postId
                                )
                        );

        if (!postLikeRepository.existsByPostPostIdAndUserUserId(
                postId,
                user.getUserId()
        )) {
            return;
        }

        postLikeRepository.deleteByPostPostIdAndUserUserId(
                postId,
                user.getUserId()
        );

        Integer lc =
                post.getLikeCount();

        post.setLikeCount(
                Math.max(
                        0,
                        (lc == null ? 0 : lc) - 1
                )
        );
    }

    // =========================================================
    // 6) 게시글 CRUD (공지 isNotice는 여기서 못 건드림)
    // =========================================================
    @Transactional
    public Long createPost(
            PostCreateRequest request
    ) {
        User user =
                userService.getCurrentUser();

        if (user == null) {
            throw new UnauthorizedException(
                    "로그인이 필요합니다."
            );
        }

        Post post =
                new Post();

        post.setTitle(
                request.title()
        );
        post.setContent(
                request.content()
        );
        post.setUser(
                user
        );
        post.setCategory(
                normalizeCreateCategory(
                        request.category()
                )
        );
        post.setIsNotice(
                false
        );

        return postRepository
                .save(
                        post
                )
                .getPostId();
    }

    @Transactional
    public Long createPostWithFiles(
            PostCreateRequest request,
            List<MultipartFile> files
    ) {
        Long postId = createPost(request);

        if (files == null || files.isEmpty()) {
            return postId;
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "존재하지 않는 게시글입니다. id=" + postId
                        )
                );

        saveAttachments(
                post,
                files
        );

        return postId;
    }

    @Transactional
    public void updatePost(
            Long postId,
            PostUpdateRequest request
    ) {
        Post post =
                postRepository.findById(
                                postId
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 게시글입니다. id=" + postId
                                )
                        );

        User user =
                userService.getCurrentUser();

        if (user == null
                || !Objects.equals(
                post.getUser().getUserId(),
                user.getUserId()
        )) {
            throw new ForbiddenException(
                    "본인의 게시글만 수정할 수 있습니다."
            );
        }

        post.setTitle(
                request.title()
        );
        post.setContent(
                request.content()
        );

        /*
         * 공지글은 일반 수정 API에서 카테고리를 바꾸지 않습니다.
         * 일반 게시글은 NOTICE 카테고리를 사용할 수 없습니다.
         */
        if (!Boolean.TRUE.equals(
                post.getIsNotice()
        ) && request.category() != null) {

            post.setCategory(
                    normalizeUpdateCategory(
                            request.category()
                    )
            );
        }
    }

    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시글입니다. id=" + postId));

        User user =
                userService.getCurrentUser();

        if (!isOwnerOrAdmin(
                user,
                post.getUser()
        )) {
            throw new ForbiddenException(
                    "게시글을 삭제할 권한이 없습니다."
            );
        }

        //충돌 방지 댓글 먼저 삭제
        commentRepository.deleteByPostPostId(postId);
        //충돌 방지 게시글 좋아요 먼저 삭제
        postLikeRepository.deleteByPostPostId(postId);
        //충돌 방지 첨부파일 삭제
        deleteAttachmentsByPostId(postId);

        postRepository.deleteById(postId);
    }

    @Transactional(readOnly = true)
    public PostAttachment getAttachment(Long attachmentId) {
        return postAttachmentRepository.findById(attachmentId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "첨부파일이 존재하지 않습니다. id="
                                        + attachmentId
                        )
                );
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource
    loadAttachmentResource(
            Long attachmentId
    ) {
        PostAttachment attachment =
                getAttachment(attachmentId);

        java.nio.file.Path path =
                fileStorageService.load(
                        attachment.getStoredFilename()
                );

        try {
            org.springframework.core.io.Resource resource =
                    new org.springframework.core.io.UrlResource(
                            path.toUri()
                    );

            if (!resource.exists()) {
                throw new NotFoundException(
                        "첨부파일을 찾을 수 없습니다."
                );
            }

            return resource;

        } catch (NotFoundException e) {
            throw e;

        } catch (Exception e) {
            throw new NotFoundException(
                    "첨부파일을 찾을 수 없습니다."
            );
        }
    }

    private void deleteAttachmentsByPostId(
            Long postId
    ) {
        List<PostAttachment> attachments =
                postAttachmentRepository
                        .findByPostPostIdOrderByCreatedAtAsc(
                                postId
                        );

        for (PostAttachment attachment : attachments) {
            fileStorageService.deleteStoredFile(
                    attachment.getStoredFilename()
            );
        }

        postAttachmentRepository
                .deleteByPostPostId(
                        postId
                );
    }

    // =========================================================
    // 7) 공지 작성: 관리자 전용
    // =========================================================
    @Transactional
    public Long createNotice(
            AdminNoticeCreateRequest request
    ) {
        User admin = requireAdmin();

        Post post = buildNotice(
                admin,
                request
        );

        return postRepository
                .save(post)
                .getPostId();
    }


    // 첨부파일이 있는 관리자 공지 작성
    @Transactional
    public Long createNoticeWithFiles(
            AdminNoticeCreateRequest request,
            List<MultipartFile> files
    ) {
        User admin = requireAdmin();

        if (!hasAtLeastOneFile(files)) {
            throw new BadRequestException(
                    "첨부파일은 최소 1개 이상 필요합니다."
            );
        }

        Post post = postRepository.save(
                buildNotice(
                        admin,
                        request
                )
        );

        saveAttachments(
                post,
                files
        );

        return post.getPostId();
    }


    // 현재 로그인 사용자가 관리자인지 확인
    private User requireAdmin() {
        User admin = userService.getCurrentUser();

        if (admin == null) {
            throw new UnauthorizedException(
                    "로그인이 필요합니다."
            );
        }

        if (!"ADMIN".equalsIgnoreCase(
                admin.getRole()
        )) {
            throw new ForbiddenException(
                    "관리자만 공지사항을 작성할 수 있습니다."
            );
        }

        return admin;
    }
    //관리자 게시글/댓글삭제
    private boolean isOwnerOrAdmin(User currentUser, User owner) {
        if (currentUser == null) {
            return false;
        }

        boolean isAdmin =
                "ADMIN".equalsIgnoreCase(
                        currentUser.getRole()
                );

        boolean isOwner =
                owner != null
                        && Objects.equals(
                        owner.getUserId(),
                        currentUser.getUserId()
                );

        return isOwner || isAdmin;
    }

    // 공지사항 엔티티 공통 생성
    private Post buildNotice(
            User admin,
            AdminNoticeCreateRequest request
    ) {
        Post post = new Post();

        post.setTitle(
                request.title()
        );
        post.setContent(
                request.content()
        );
        post.setUser(
                admin
        );
        post.setCategory(
                "NOTICE"
        );
        post.setIsNotice(
                true
        );

        return post;
    }
    private boolean hasAtLeastOneFile(
            List<MultipartFile> files
    ) {
        return files != null
                && files.stream()
                .anyMatch(
                        file -> file != null
                                && !file.isEmpty()
                );
    }


    private void saveAttachments(
            Post post,
            List<MultipartFile> files
    ) {
        if (files == null
                || files.isEmpty()) {

            return;
        }

        List<String> storedFilenames =
                new ArrayList<>();

        registerRollbackFileCleanup(
                storedFilenames
        );

        try {
            for (MultipartFile file : files) {
                if (file == null
                        || file.isEmpty()) {

                    continue;
                }

                FileStorageService.StoredFile stored =
                        fileStorageService.store(
                                file
                        );

                storedFilenames.add(
                        stored.storedFilename()
                );

                PostAttachment attachment =
                        new PostAttachment();

                attachment.setPost(
                        post
                );
                attachment.setOriginalFilename(
                        stored.originalFilename()
                );
                attachment.setStoredFilename(
                        stored.storedFilename()
                );
                attachment.setContentType(
                        stored.contentType()
                );
                attachment.setFileSize(
                        stored.size()
                );

                postAttachmentRepository.save(
                        attachment
                );
            }

        } catch (RuntimeException e) {
            if (!TransactionSynchronizationManager
                    .isSynchronizationActive()) {

                cleanupStoredFiles(
                        storedFilenames
                );
            }

            throw e;
        }
    }
    private void registerRollbackFileCleanup(
            List<String> storedFilenames
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (status == STATUS_ROLLED_BACK) {
                                    cleanupStoredFiles(
                                            storedFilenames
                                    );
                                }
                            }
                        }
                );
    }


    private void cleanupStoredFiles(
            List<String> storedFilenames
    ) {
        for (String storedFilename : storedFilenames) {
            fileStorageService.deleteStoredFile(
                    storedFilename
            );
        }
    }
}
