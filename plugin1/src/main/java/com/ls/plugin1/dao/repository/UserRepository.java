package com.ls.plugin1.dao.repository;

import com.ls.plugin1.dao.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * UserRepository
 *
 * @author yangzj
 * @version 1.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
}
