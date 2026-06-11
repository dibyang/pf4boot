package net.xdob.sample.userbook.repository;

import net.xdob.sample.model.userbook.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 图书 Repository。
 */
@Repository
public interface BookRepository extends JpaRepository<Book, String> {

  @Modifying
  @Query("delete from Book where author=:author")
  int deleteByAuthor(@Param("author") String author);
}
