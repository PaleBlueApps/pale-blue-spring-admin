CREATE TABLE IF NOT EXISTS users
(
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL,
    email TEXT NOT NULL,
    age INTEGER
);

INSERT OR IGNORE INTO users (id, username, email, age) VALUES (1, 'biplab', 'biplab@example.com', 25);
INSERT OR IGNORE INTO users (id, username, email, age) VALUES (2, 'anil', 'anil@example.com', 26);
INSERT OR IGNORE INTO users (id, username, email, age) VALUES (3, 'michael', 'michael@example.com', 27);
INSERT OR IGNORE INTO users (id, username, email, age) VALUES (4, 'mike', 'mike@example.com', 28);
