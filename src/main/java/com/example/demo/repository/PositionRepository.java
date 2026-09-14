package com.example.demo.repository;

import com.example.demo.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByUserAccountIdAndStockId(Long userAccountId, Long stockId);

    /** All of a user's positions (open and closed) with their stock, oldest first — one query. */
    @Query("""
            select p from Position p
            join fetch p.stock
            where p.userAccount.id = :userId
            order by p.id asc
            """)
    List<Position> findAllForUser(@Param("userId") Long userId);

    long deleteByUserAccountId(Long userAccountId);
}
