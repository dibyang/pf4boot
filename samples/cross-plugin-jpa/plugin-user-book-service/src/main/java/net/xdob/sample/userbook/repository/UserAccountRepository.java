package net.xdob.sample.userbook.repository;

import net.xdob.sample.model.userbook.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户 Repository。
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
}
