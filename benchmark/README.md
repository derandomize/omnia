# Результаты измерений

| Client Type | Index Count | Score (ms/op) |
|:------------|------------:|--------------:|
| **STANDARD**| 10          |         0.585 |
|             | 400         |         0.601 |
|             | 800         |          DEAD |
|             | 2,000       |          DEAD |
|             | 5,000       |          DEAD |
|             | 10,000      |          DEAD |
|             | 50,000      |          DEAD |
|             | 100,000     |          DEAD |
| **OMNIA**   | 10          |         0.882 |
|             | 400         |         0.916 |
|             | 800         |         0.860 |
|             | 2,000       |         0.940 |
|             | 5,000       |         0.923 |
|             | 10,000      |         0.865 |
|             | 50,000      |         0.946 |
|             | 100,000     |         0.938 |
