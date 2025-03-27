CREATE TABLE index_to_commune
(
    "index"   VARCHAR PRIMARY KEY,
    "commune" VARCHAR NOT NULL
);


CREATE TABLE omnia_config
(
    "key"   VARCHAR PRIMARY KEY,
    "value" VARCHAR
);