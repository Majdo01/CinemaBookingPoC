package dk.via.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "reservations")
public class ReservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "screening_id", nullable = false)
  private ScreeningEntity screening;

  @Column(name = "seat_number", nullable = false)
  private String seatNumber;

  public ReservationEntity(ScreeningEntity screening, String seatNumber) {
    this.screening = screening;
    this.seatNumber = seatNumber;
  }

  public ReservationEntity() {
  }

  public Long getId() {
    return id;
  }

  public ScreeningEntity getScreening() {
    return screening;
  }

  public String getSeatNumber() {
    return seatNumber;
  }
}
