package com.ls.demo.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * BookRepository
 *
 * @author yangzj
 * @version 1.0
 */
@Repository
public interface BookRepository extends JpaRepository<Book, String> {
}
