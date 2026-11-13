# Деканат

`fun-student` — веб-приложение для просмотра и ведения реестра студентов. Интерфейс, REST API и статические файлы обслуживает Spring Boot. Постоянные данные хранятся в PostgreSQL.

## Быстрый запуск

Для просмотра интерфейса откройте [preview.html](preview.html) из папки проекта. В браузере можно менять роли и темы, искать записи, открывать карточки, добавлять, редактировать и удалять записи. Данные предпросмотра сбрасываются при обновлении страницы.

Чтобы проверить вход, серверные права и сохранение изменений, запустите приложение с профилем `demo`. Нужен JDK 21. При первом запуске Maven Wrapper загружает Maven и зависимости.

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

В Linux и macOS выполните `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`. Откройте [приложение](http://localhost:8080/). Демо использует H2 в памяти. При остановке процесса изменения сбрасываются.

Если JAR уже собран командой `mvnw.cmd clean verify`, его можно запустить напрямую.

```powershell
java -jar .\target\student-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

| Почта | Пароль | Права |
| --- | --- | --- |
| `teacher@example.com` | `teacher` | Просмотр и поиск студентов |
| `admin@example.com` | `admin` | Просмотр, создание, редактирование и удаление студентов |
| `superadmin@example.com` | `superAdmin` | Управление учётными записями и ролями |

Права нескольких ролей суммируются. Сервер проверяет их при каждом запросе. Администратор студентов может изменить имя, фамилию и контакты. Контакты необязательны.

## Запуск с PostgreSQL

Создайте базу `students_db` в PostgreSQL или запустите её через Docker Compose. Для первого запуска задайте параметры в том же терминале. Подставьте свои пароли перед выполнением команд.

```powershell
$env:STUDENT_DB_PASSWORD = '<пароль PostgreSQL>'
$env:STUDENT_BOOTSTRAP_EMAIL = 'operator@example.com'
$env:STUDENT_BOOTSTRAP_PASSWORD = '<пароль первой учётной записи от 12 символов>'
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

Для уже запущенной PostgreSQL пропустите команду `docker compose` и укажите её параметры. Учётной записи базы нужен доступ к схеме `students_schema`. Приложение создаст схему при её отсутствии, если у пользователя базы есть право `CREATE`. Для поиска по кириллице без учёта регистра используйте локаль с поддержкой Unicode. Compose инициализирует новую базу с локалью ICU `und`. Для нестандартного порта базы задайте `STUDENT_DB_PORT` и укажите тот же порт в `STUDENT_DB_URL`.

| Переменная | Назначение |
| --- | --- |
| `STUDENT_DB_URL` | JDBC URL. По умолчанию `jdbc:postgresql://localhost:5432/students_db` |
| `STUDENT_DB_USER` | Пользователь базы. По умолчанию `postgres` |
| `STUDENT_DB_PASSWORD` | Пароль пользователя базы |
| `STUDENT_BOOTSTRAP_EMAIL` и `STUDENT_BOOTSTRAP_PASSWORD` | Создают начальную учётную запись с ролью `SUPER_ADMIN`, если такого email ещё нет |
| `STUDENT_JWT_SECRET` | Секрет подписи в Base64, минимум 32 байта после декодирования. Без переменной ключ генерируется при каждом запуске, поэтому после перезапуска нужно войти заново |
| `SERVER_PORT` | HTTP-порт приложения. По умолчанию `8080` |

После первого запуска войдите как `SUPER_ADMIN`, создайте рабочие учётные записи и уберите `STUDENT_BOOTSTRAP_EMAIL` и `STUDENT_BOOTSTRAP_PASSWORD` из окружения. Существующие учётные записи сохраняют свои пароли и роли.

## API

`POST /api/v1/auth/login` принимает `email` и `password`, возвращает `token`, `type`, `expiresIn` и `roles`. В защищённых запросах передавайте `Authorization: Bearer <token>`.

| Операция | Маршрут |
| --- | --- |
| Список студентов | `GET /api/v1/student` |
| Карточка студента | `GET /api/v1/student/{id}` или `GET /api/v1/student/info/{id}` |
| Добавление студента | `POST /api/v1/student/save` |
| Изменение или удаление студента | `PUT` или `DELETE /api/v1/student/{id}` |
| Список учётных записей | `GET /api/v1/users` |
| Создание учётной записи | `POST /api/v1/users/save` |
| Изменение или удаление учётной записи | `PUT` или `DELETE /api/v1/users/{id}` |

Список студентов поддерживает `query` по имени, фамилии и ID, `sort=id|name|surname`, `direction=asc|desc`, `page` с нуля и `size` от 1 до 100. `totalCount` показывает число совпадений по всем страницам. Без параметров пагинации возвращается весь список. Для частичного изменения студента передавайте только нужные поля. Пустая строка очищает телефон или почту. При обновлении учётной записи передавайте `email` и `roles`. Пропущенный или пустой `password` сохраняет текущий пароль.

Команды и сценарий проверки описаны в [TESTING.md](TESTING.md).

Исходный серверный проект — [dorzhogun/fun-student](https://github.com/dorzhogun/fun-student). Ориентир по функциональности интерфейса — [wtfnur/fun-student](https://github.com/wtfnur/fun-student).
