package com.example.lightsafe.post;

import java.io.Serializable;
import java.util.Objects;

public class PostLikeId implements Serializable {
    private Long post; //만약 org.hibernate.MappingException:proprty has wrong type
    private Long user; //뜨는 경우 post>postId user> userId
                        //이거도 안되면 @EmbeddedId+@MapId로
    public PostLikeId() {}

    public PostLikeId(Long post, Long user) {
        this.post = post;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostLikeId that = (PostLikeId) o;
        return Objects.equals(post, that.post) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(post, user);
    }

}
