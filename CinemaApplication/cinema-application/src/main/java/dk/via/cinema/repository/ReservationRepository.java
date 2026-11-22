package dk.via.cinema.repository;

import dk.via.cinema.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository
    extends JpaRepository<ReservationEntity, Long> {
}

