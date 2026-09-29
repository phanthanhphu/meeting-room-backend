# Meeting Room Backend - MongoDB / Gradle

Spring Boot 3.3.5 + Java 17 + Spring Security + Spring Data MongoDB, built with Gradle.

## Database
The backend now uses MongoDB only. SQL/JPA/H2/MySQL have been removed.

Default connection:
- MongoDB server: `mongodb://localhost:27017`
- Database name: `meeting_room_booking`

`src/main/resources/application.yml`:
```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017}
      database: ${MONGODB_DATABASE:meeting_room_booking}
      auto-index-creation: true
```

If the sample project already points to another MongoDB server, reuse that server URI and keep the new database name:

Windows CMD:
```bat
set MONGODB_URI=mongodb://YOUR_MONGO_SERVER:27017
set MONGODB_DATABASE=meeting_room_booking
gradle bootRun
```

PowerShell:
```powershell
$env:MONGODB_URI="mongodb://YOUR_MONGO_SERVER:27017"
$env:MONGODB_DATABASE="meeting_room_booking"
gradle bootRun
```

Linux/macOS:
```bash
export MONGODB_URI=mongodb://YOUR_MONGO_SERVER:27017
export MONGODB_DATABASE=meeting_room_booking
gradle bootRun
```

For authenticated MongoDB, `MONGODB_URI` can include username/password/authSource, for example:
```text
mongodb://user:password@host:27017/?authSource=admin
```
The application data still goes to `meeting_room_booking` through `MONGODB_DATABASE`.

## Collections
The application creates/uses:
- `users`
- `meeting_rooms`
- `bookings`
- `booking_history`
- `room_approval_locks`

## Run
Requirements:
- JDK 17
- Gradle 8.x
- MongoDB running and reachable

```bash
gradle bootRun
```
API: `http://localhost:8084/api`

Demo seed accounts on an empty database:
- Admin: `admin / admin123`
- User: `user / user123`

## Build
```bash
gradle clean build
```
JAR output: `build/libs/`

## Booking rules
- Login required.
- Booking starts as `PENDING`.
- Admin approves/rejects requests.
- Start < end; no past booking; same day only.
- Business hours 07:30-18:00.
- Minimum 15 minutes, maximum 4 hours, maximum 90 days in advance.
- Attendee count cannot exceed room capacity.
- MAINTENANCE/DISABLED rooms cannot be booked.
- APPROVED room conflicts are blocked.
- User cannot overlap their own PENDING/APPROVED bookings.
- Editing an APPROVED booking sends it back to PENDING.
- Admin approval rechecks overlap and uses a MongoDB room lease lock to protect concurrent approvals.
- Used rooms cannot be deleted; disable them instead.
