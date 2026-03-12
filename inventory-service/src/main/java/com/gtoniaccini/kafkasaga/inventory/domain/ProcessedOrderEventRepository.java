package com.gtoniaccini.kafkasaga.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEvent, Long> {
}
