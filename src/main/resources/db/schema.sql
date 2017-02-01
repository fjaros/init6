GRANT ALL PRIVILEGES ON *.* TO 'filip'@'localhost' IDENTIFIED BY '12345' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'replicator'@'%' IDENTIFIED BY '8init6gz47z0';
GRANT ALL PRIVILEGES ON *.* TO 'vilenet'@'localhost' IDENTIFIED BY 'picakokot';

DROP DATABASE vilenet;
CREATE SCHEMA vilenet;
USE vilenet;

CREATE TABLE IF NOT EXISTS users (
    id INT(11) NOT NULL AUTO_INCREMENT,
    alias_id INT(11),
    username VARCHAR(16) NOT NULL,
    password_hash VARBINARY(20) NOT NULL,
    flags BIT(32) NOT NULL DEFAULT 0,
    closed BOOLEAN DEFAULT FALSE,
    closed_reason VARCHAR(255),
    PRIMARY KEY(id),
    UNIQUE KEY(username)
);

CREATE TABLE IF NOT EXISTS channel_joins (
    id INT(11) NOT NULL AUTO_INCREMENT,
    server_id TINYINT NOT NULL,
    user_id INT(11) NOT NULL,
    alias_id INT(11),
    channel VARCHAR(255) NOT NULL,
    server_accepting_time BIGINT NOT NULL,
    channel_created_time BIGINT NOT NULL,
    joined_time BIGINT NOT NULL,
    joined_place INT NOT NULL,
    is_operator BOOLEAN NOT NULL,
    PRIMARY KEY(id)
);
