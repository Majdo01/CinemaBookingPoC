package dk.via.cinema.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "screenings")
public class ScreeningEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "movie_title", nullable = false)
  private String movieTitle;

  @Column(name = "start_time", nullable = false)
  private LocalDateTime startTime;

  @Column(nullable = false)
  private String room;

  public ScreeningEntity(String movieTitle, LocalDateTime startTime, String room){
    this.movieTitle = movieTitle;
    this.startTime = startTime;
    this.room = room;
  }

  public ScreeningEntity() {
  }

  public Long getId() {
    return id;
  }

  public String getMovieTitle() {
    return movieTitle;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public String getRoom() {
    return room;
  }
}
