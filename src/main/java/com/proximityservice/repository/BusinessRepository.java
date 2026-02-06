package com.proximityservice.repository;

import com.proximityservice.domain.Business;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, String> {

    List<Business> findAllByIdIn(List<String> ids);
}
