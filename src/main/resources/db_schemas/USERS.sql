GRANT ALL PRIVILEGES ON *.* TO 'vilenet'@'localhost' IDENTIFIED BY 'picakokot' WITH GRANT OPTION;
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
