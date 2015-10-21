CREATE TABLE IF NOT EXISTS users (
    id INT(11) NOT NULL AUTO_INCREMENT,
    username VARCHAR(16) NOT NULL,
    password VARCHAR(256) NOT NULL,
    flags BIT(32) NOT NULL DEFAULT 0,
    PRIMARY KEY(id),
    UNIQUE KEY(username)
);

INSERT INTO users(username, password, flags) VALUES ('l2k-shadow', 'vile123456', b'10001');
INSERT INTO users(username, password, flags) VALUES ('hackez', 'balls', b'10001');
INSERT INTO users(username, password) VALUES ('chat1', 'balls');
INSERT INTO users(username, password) VALUES ('chat2', 'balls');
