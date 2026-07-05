CREATE TABLE greeting (
  id BIGSERIAL PRIMARY KEY,
  message TEXT NOT NULL
);

INSERT INTO greeting (message) VALUES ('hello');
