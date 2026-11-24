package dk.via.cinema.repository;

import dk.via.cinema.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository
    extends JpaRepository<ReservationEntity, Long> {

  /** All reservations of one screening, oldest first, for a stable response. */
  List<ReservationEntity> findByScreening_IdOrderByIdAsc(Long screeningId);

  /**
   * Cheap pre-check so a taken seat is rejected without touching the database
   * write path. The unique constraint on (screening_id, seat_number) is still
   * what makes a duplicate reservation impossible.
   */
  boolean existsByScreening_IdAndSeatNumber(Long screeningId, String seatNumber);
}
