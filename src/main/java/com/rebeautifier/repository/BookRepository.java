package com.rebeautifier.repository;

import com.rebeautifier.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID> {

    @Query("""
            select distinct b from Book b
            left join BookMember m on m.book = b
            where b.owner.id = :userId or m.user.id = :userId
            order by b.updatedAt desc
            """)
    List<Book> findAccessibleByUser(@Param("userId") UUID userId);
}
