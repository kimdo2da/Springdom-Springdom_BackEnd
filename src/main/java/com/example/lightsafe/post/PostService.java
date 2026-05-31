package com.example.lightsafe.post;

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import com.example.lightsafe.user.UserService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public CommunityPostsResponse getCommunity(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? 10 : size;

        List<PostListResponse> notices = postRepository
                .findTop3ByIsNoticeTrueOrderByCreatedAtDesc()
                .stream()
                .map(PostListResponse::from)
                .toList();

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> postPage = postRepository.findByIsNoticeFalse(pageable);

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

        return new CommunityPostsResponse(notices, items, pageInfo);
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
    public PostListPageResponse getPostsByCategory(String category, int page, int size, String sort) {
        Sort sortSpec = buildSearchSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortSpec);
        Page<Post> postPage = postRepository.findByCategory(category, pageable);

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

    @Transactional(readOnly = true)
    public PostListPageResponse searchPosts(String keyword, int page, int size, String sort) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("게시글이 존재하지 않습니다");
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

        User user = userService.getCurrentUser();
        if (user == null) throw new IllegalArgumentException("로그인이 필요합니다.");

        Comment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부모 댓글입니다. id=" + request.parentId()));

            if (!Objects.equals(parent.getPost().getPostId(), postId)) {
                throw new IllegalArgumentException("부모 댓글이 해당 게시글에 속하지 않습니다.");
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
    public void updateComment(Long commentId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다. id=" + commentId));

        User user = userService.getCurrentUser();
        if (user == null || !Objects.equals(comment.getUser().getUserId(), user.getUserId())) {
            throw new IllegalArgumentException("본인의 댓글만 수정할 수 있습니다.");
        }

        comment.setContent(content);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

        Comment target = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다. id=" + commentId));

        User user = userService.getCurrentUser();
        if (user == null || !Objects.equals(target.getUser().getUserId(), user.getUserId())) {
            throw new IllegalArgumentException("본인의 댓글만 삭제할 수 있습니다.");
        }

        if (!Objects.equals(target.getPost().getPostId(), postId)) {
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
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


    // =========================================================
    // 5) 좋아요 / 좋아요 취소
    // =========================================================
    @Transactional
    public void likePost(Long postId) {
        User user = userService.getCurrentUser();
        if (user == null) throw new IllegalArgumentException("로그인이 필요합니다.");

        if (postLikeRepository.existsByPostPostIdAndUserUserId(postId, user.getUserId())) return;

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

        PostLike like = new PostLike();
        like.setPost(post);
        like.setUser(user);
        postLikeRepository.save(like);

        Integer lc = post.getLikeCount();
        post.setLikeCount((lc == null ? 0 : lc) + 1);
    }

    @Transactional
    public void unlikePost(Long postId) {
        User user = userService.getCurrentUser();
        if (user == null) throw new IllegalArgumentException("로그인이 필요합니다.");

        if (!postLikeRepository.existsByPostPostIdAndUserUserId(postId, user.getUserId())) return;

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

        postLikeRepository.deleteByPostPostIdAndUserUserId(postId, user.getUserId());

        Integer lc = post.getLikeCount();
        post.setLikeCount(Math.max(0, (lc == null ? 0 : lc) - 1));
    }

    // =========================================================
    // 6) 게시글 CRUD (공지 isNotice는 여기서 못 건드림)
    // =========================================================
    @Transactional
    public Long createPost(PostCreateRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. id=" + request.userId()));

        Post post = new Post();
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setUser(user);
        post.setCategory(request.category() != null ? request.category() : "INFO"); // ✅ 카테고리 추가
        post.setIsNotice(false); // ✅ 일반 작성은 무조건 일반글

        return postRepository.save(post).getPostId();
    }

    @Transactional
    public Long createPostWithFiles(PostCreateRequest request, java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        Long postId = createPost(request);

        if (files == null || files.isEmpty()) {
            return postId;
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

        for (org.springframework.web.multipart.MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            FileStorageService.StoredFile stored = fileStorageService.store(file);
            PostAttachment attachment = new PostAttachment();
            attachment.setPost(post);
            attachment.setOriginalFilename(stored.originalFilename());
            attachment.setStoredFilename(stored.storedFilename());
            attachment.setContentType(stored.contentType());
            attachment.setFileSize(stored.size());
            postAttachmentRepository.save(attachment);
        }

        return postId;
    }

    @Transactional
    public PostResponse updatePost(Long postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

        User user = userService.getCurrentUser();
        if (user == null || !Objects.equals(post.getUser().getUserId(), user.getUserId())) {
            throw new IllegalArgumentException("본인의 게시글만 수정할 수 있습니다.");
        }

        post.setTitle(request.title());
        post.setContent(request.content());

        // 공지글이 아닐 때만 카테고리 변경 허용 (보안/데이터 정합성)
        if (!post.getIsNotice() && request.category() != null) {
            post.setCategory(request.category());
        }

        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. id=" + postId));

        User user = userService.getCurrentUser();
        if (user == null || !Objects.equals(post.getUser().getUserId(), user.getUserId())) {
            throw new IllegalArgumentException("본인의 게시글만 삭제할 수 있습니다.");
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
                .orElseThrow(() -> new IllegalArgumentException("첨부파일이 존재하지 않습니다. id=" + attachmentId));
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource loadAttachmentResource(Long attachmentId) {
        PostAttachment attachment = getAttachment(attachmentId);
        java.nio.file.Path path = fileStorageService.load(attachment.getStoredFilename());
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
            if (!resource.exists()) {
                throw new IllegalArgumentException("첨부파일을 찾을 수 없습니다.");
            }
            return resource;
        } catch (Exception e) {
            throw new IllegalArgumentException("첨부파일을 찾을 수 없습니다.");
        }
    }

    private void deleteAttachmentsByPostId(Long postId) {
        List<PostAttachment> attachments = postAttachmentRepository.findByPostPostIdOrderByCreatedAtAsc(postId);
        for (PostAttachment attachment : attachments) {
            try {
                java.nio.file.Files.deleteIfExists(fileStorageService.load(attachment.getStoredFilename()));
            } catch (Exception ignored) {
            }
        }
        postAttachmentRepository.deleteByPostPostId(postId);
    }

    // =========================================================
    // 7) 공지 작성: 관리자 전용
    // =========================================================
    @Transactional
    public Long createNotice(AdminNoticeCreateRequest request) {
        User admin = userRepository.findById(request.adminUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. id=" + request.adminUserId()));

        Post post = new Post();
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setUser(admin);
        post.setCategory("NOTICE"); // ✅ 카테고리 고정
        post.setIsNotice(true); // ✅ 여기서만 공지 생성

        return postRepository.save(post).getPostId();
    }
}
