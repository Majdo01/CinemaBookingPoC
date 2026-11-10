package dk.via.cinema.repository;

import dk.via.cinema.entity.ScreeningEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRepository
    extends JpaRepository<ScreeningEntity, Long> {
}
