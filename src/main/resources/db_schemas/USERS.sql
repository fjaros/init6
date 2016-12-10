GRANT ALL PRIVILEGES ON *.* TO 'filip'@'localhost' IDENTIFIED BY '12345' WITH GRANT OPTION;
DROP DATABASE vilenet;
CREATE SCHEMA vilenet;
USE vilenet;

CREATE TABLE IF NOT EXISTS users (
    id INT(11) NOT NULL AUTO_INCREMENT,
    username VARCHAR(16) NOT NULL,
    password_hash VARBINARY(20) NOT NULL,
    flags BIT(32) NOT NULL DEFAULT 0,
    PRIMARY KEY(id),
    UNIQUE KEY(username)
);

--INSERT INTO users(username, password, flags) VALUES ('l2k-shadow', 'vile123456', b'10001');
--INSERT INTO users(username, password, flags) VALUES ('hackez', 'balls', b'10001');
--INSERT INTO users(username, password) VALUES ('chat1', 'balls');
--INSERT INTO users(username, password) VALUES ('chat2', 'balls');

CREATE TABLE IF NOT EXISTS user_bans (
    id INT(11) NOT NULL AUTO_INCREMENT,
    username VARCHAR(16),
    banned_by VARCHAR(16),
    banned_time TIMESTAMP,
    banned_until TIMESTAMP,
    PRIMARY KEY(id),
    UNIQUE KEY(username)
);

CREATE TABLE IF NOT EXISTS ip_bans (
    id INT(11) NOT NULL AUTO_INCREMENT,
    ip_address VARCHAR(15) NOT NULL,
    username VARCHAR(16),
    banned_by VARCHAR(16),
    banned_time TIMESTAMP,
    banned_until TIMESTAMP,
    PRIMARY KEY(id),
    UNIQUE KEY(ip_address)
);
