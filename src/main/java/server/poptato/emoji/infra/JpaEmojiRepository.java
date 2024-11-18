package server.poptato.emoji.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import server.poptato.emoji.domain.entity.Emoji;
import server.poptato.emoji.domain.repository.EmojiRepository;

public interface JpaEmojiRepository extends EmojiRepository, JpaRepository<Emoji, Long> {
    @Query("SELECT e.imageUrl FROM Emoji e WHERE e.id = :emojiId")
    String findImageUrlById(@Param("emojiId") Long emojiId);
}
