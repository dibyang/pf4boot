package com.ls.demo.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * BookRepository
 *
 * @author yangzj
 * @version 1.0
 */
@Repository
public interface BookRepository extends JpaRepository<Book, String> {

  @Transactional
  @Modifying
  @Query("delete from Book where author=:author")
  int deleteByAuthor(@Param("author") String author);
}
