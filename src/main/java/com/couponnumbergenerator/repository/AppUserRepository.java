package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.UserSyncStatus;
import com.couponnumbergenerator.model.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    Page<AppUser> findBySyncStatusIn(Collection<UserSyncStatus> syncStatuses, Pageable pageable);
}
