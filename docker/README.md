# Docker-окружение для проекта Omnia

## Структура каталога

- `Dockerfile` - файл для сборки образа migrator
- `entrypoint.sh` - точка входа для контейнера migrator
- `config.yml` - конфигурационный файл для migrator

## Запуск окружения

Для запуска всего окружения выполните в корне проекта:

```bash
docker-compose up --build -d
```

Эта команда соберёт образ migrator и запустит все необходимые сервисы.

## Управление migrator-демоном

После запуска контейнеров вы можете управлять демоном migrator с помощью следующих команд:
```bash
# Проверка статуса демона
docker-compose exec migrator /app/entrypoint.sh daemon status
```
```bash
# Остановка демона
docker-compose exec migrator /app/entrypoint.sh daemon stop
```
```bash
# Запуск демона
docker-compose exec migrator /app/entrypoint.sh daemon start
```
```bash
# Перезапуск демона
docker-compose exec migrator /app/entrypoint.sh daemon restart
```

## Изменение конфигурации

Конфигурационный файл `config.yml` монтируется в контейнер как volume. Вы можете изменить его на хосте, а затем перезапустить демон:

1. Отредактируйте файл `config.yml` в контейнере
```bash
docker-compose exec migrator vi /app/config.yml
```
2. Перезапустите демон:
```bash
docker-compose exec migrator /app/entrypoint.sh daemon restart
```

## Просмотр логов

Для просмотра логов migrator-демона:

```bash
docker-compose exec migrator cat /var/log/migration-daemon.log
```
