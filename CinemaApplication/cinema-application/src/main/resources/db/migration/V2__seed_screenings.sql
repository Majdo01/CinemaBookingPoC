-- Sample screenings so a fresh database is not empty when the client starts.
--
-- start_time is computed relative to CURRENT_DATE rather than hard coded, so
-- the demo data is always upcoming no matter when the database is created.
-- Ids are omitted and left to the identity column.
INSERT INTO screenings (movie_title, start_time, room) VALUES
    ('Dune: Part Two',           (CURRENT_DATE + TIME '18:30') + INTERVAL '1 day',  'Sal 1'),
    ('Dune: Part Two',           (CURRENT_DATE + TIME '21:15') + INTERVAL '1 day',  'Sal 2'),
    ('Oppenheimer',              (CURRENT_DATE + TIME '19:00') + INTERVAL '2 days', 'Sal 1'),
    ('The Grand Budapest Hotel', (CURRENT_DATE + TIME '16:45') + INTERVAL '2 days', 'Sal 3'),
    ('Perfect Days',             (CURRENT_DATE + TIME '20:00') + INTERVAL '3 days', 'Sal 2');
