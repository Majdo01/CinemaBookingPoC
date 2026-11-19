# UML Class Diagram – Cinema Booking System

A plain, hand-laid-out UML class diagram of the code that actually exists in this
repository. No architecture-poster styling: white boxes, black lines, three
compartments per class, standard UML multiplicities.

| File | What it is |
| --- | --- |
| `class-diagram.py` | Editable diagram source (class boxes, positions, relationships) |
| `class-diagram.svg` | Vector export – opens in Inkscape, Illustrator, draw.io, browsers |
| `class-diagram.png` | Raster export, 4480 × 3480 px (150 dpi) |

Regenerate both exports after editing the source:

```bash
python docs/uml/class-diagram.py
```

Requires Python 3 with Pillow (`pip install pillow`). The script draws the same
scene twice: once as SVG, once as PNG.

## How to edit

The file is deliberately declarative:

* `CLASSES` – one `klass(...)` entry per box: `name`, `stereotype`, `attrs`,
  `methods`, `x`, `y`, `w`. Height is computed from the compartment contents.
* `RELATIONS` – one `dict` per line: `kind` (`dep`, `assoc`, `inherit`,
  `realize`, `composition`), an anchor on each class, optional `via` waypoints,
  labels and multiplicities.
* Anchors are absolute coordinates on a box edge, e.g. `("home", "r", 200)` is
  the point 200 px down the right edge of `Home`; `("home", "b", 225)` is the
  point 225 px along the bottom edge. This keeps the routing orthogonal and
  readable instead of letting an auto-layout engine sprawl the diagram.

## Classes and where they come from

| Class | Source |
| --- | --- |
| `Home`, `Home.ScreeningDto` | `CinemaClient/Components/Pages/Home.razor` |
| `Program` | `CinemaApi/Program.cs` (minimal API endpoints) |
| `CreateScreeningRequestDto` | `CinemaApi/Program.cs` (record at the end of the file) |
| `ScreeningService`, `Screening`, `ScreeningList`, `CreateScreeningRequest`, `CreateScreeningResponse` | `CinemaApi/Protos/screenings.proto`, `CinemaApplication/.../src/main/proto/screenings.proto` |
| `ScreeningServiceClient` | generated C# client (`GrpcServices="Client"`) |
| `ScreeningServiceImplBase` | generated Java base (`ScreeningServiceGrpc`) |
| `CinemaApplication` | `dk/via/cinema_application/CinemaApplication.java` |
| `GrpcServerConfig` | `dk/via/cinema/service/GrpcServerConfig.java` |
| `ScreeningGrpcService` | `dk/via/cinema/service/ScreeningGrpcService.java` |
| `ScreeningRepository` | `dk/via/cinema/repository/ScreeningRepository.java` |
| `ScreeningEntity` | `dk/via/cinema/entity/ScreeningEntity.java` |
| `screenings` | `db/migration/V1__create_screenings_table.sql` |

`EmptyRequest` is an empty marker message, so it is not drawn as a box; it only
appears in the `listScreenings` signatures.

## Relationships and multiplicities

| Relationship | Kind | Multiplicity | Evidence in code |
| --- | --- | --- | --- |
| `Home` → `Program` | dependency `«HTTP GET /api/screenings»` | 1 → 1 | `Home.LoadScreenings` calls `api/screenings` on the `"CinemaApi"` `HttpClient` |
| `Home` ◆ `ScreeningDto` | composition | 1 → 0..* | `List<ScreeningDto> screenings` is created and owned by the component |
| `Program` → `CreateScreeningRequestDto` | dependency `«uses»` | – | parameter of the `MapPost` handler |
| `Program` → `ScreeningServiceClient` | dependency `«creates per request»` | 1 → 0..* | a new `GrpcChannel` and client are built inside each endpoint |
| `ScreeningServiceClient` → `ScreeningService` | dependency `«gRPC over HTTP/2»` | – | generated client targets the `ScreeningService` service |
| `ScreeningServiceClient` → `CreateScreeningRequest` | dependency `«sends»` | – | argument of `CreateScreeningAsync` |
| `ScreeningServiceClient` → `CreateScreeningResponse` | dependency `«receives»` | – | result of `CreateScreeningAsync` |
| `ScreeningList` ◆ `Screening` | composition | 1 → 0..* | `repeated Screening screenings = 1` |
| `CreateScreeningResponse` ◆ `Screening` | composition | 1 → 0..1 | `Screening screening = 3` (message field, unset when absent) |
| `ScreeningServiceImplBase` ⇢ `ScreeningService` | realization | – | generated base implements the service contract |
| `ScreeningGrpcService` ▷ `ScreeningServiceImplBase` | generalization | – | `extends ScreeningServiceGrpc.ScreeningServiceImplBase` |
| `ScreeningGrpcService` → `ScreeningRepository` | directed association `«uses»` | 1 → 1 | `private final ScreeningRepository screeningRepository` |
| `GrpcServerConfig` → `ScreeningGrpcService` | directed association `«registers»` | 1 → 1 | `ServerBuilder.addService(screeningGrpcService)` |
| `CinemaApplication` → `GrpcServerConfig` | dependency `«bootstraps»` | – | Spring Boot component scan starts the `@Configuration` |
| `ScreeningRepository` → `ScreeningEntity` | directed association `«persists»` | 1 → 0..* | `JpaRepository<ScreeningEntity, Long>` manages all rows |
| `ScreeningEntity` → `screenings` | dependency `«maps to»` | – | `@Table(name = "screenings")` |

## What the diagram deliberately does *not* claim

* `createScreening` is declared in the proto contract and called by the C# API,
  but `ScreeningGrpcService` overrides **only** `listScreenings`. The concrete
  default in the generated base returns `UNIMPLEMENTED`, which the C# API maps
  to HTTP 501. This is shown in the attached UML note.
* The Blazor client has no create form and never calls `POST /api/screenings`;
  only the read path is wired from the UI.
* `ScreeningEntity` has a no-argument constructor and getters only, so the
  create flow has no way to build an entity yet.
