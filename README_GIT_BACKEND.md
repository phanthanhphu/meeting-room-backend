# Push Meeting Room Backend lên GitHub

Thư mục hiện tại:

```text
D:\Booking Meeting\meeting-room-booking\meeting-room-backend
```

## 1. Tạo file `.gitignore`

Trong thư mục backend, tạo file `.gitignore` với nội dung:

```gitignore
.gradle/
.idea/
build/
out/
*.iml
*.log
.env
src.rar
```

> `src.rar` chỉ là file nén dự phòng, không cần push nếu source thật đã nằm trong thư mục `src`.

## 2. Mở CMD/Terminal tại thư mục backend

```cmd
cd /d "D:\Booking Meeting\meeting-room-booking\meeting-room-backend"
```

## 3. Khởi tạo Git

```cmd
git init
git add .
git status
```

Kiểm tra trước khi commit: không nên thấy `.gradle`, `.idea`, `build`.

## 4. Commit lần đầu

```cmd
git commit -m "Initial backend commit"
git branch -M main
```

## 5. Kết nối GitHub

Sau khi tạo repository backend trên GitHub, lấy URL HTTPS.

Ví dụ nếu repository có tên `meeting-room-backend`:

```cmd
git remote add origin https://github.com/phanthanhphu/meeting-room-backend.git
```

Kiểm tra:

```cmd
git remote -v
```

## 6. Push lên GitHub

```cmd
git push -u origin main
```

## Nếu báo `remote origin already exists`

Dùng:

```cmd
git remote set-url origin https://github.com/phanthanhphu/meeting-room-backend.git
git push -u origin main
```

## Những lần sau

Mỗi lần sửa code:

```cmd
git add .
git commit -m "Update backend"
git push
```

## Lưu ý

Không dùng repository `meeting-room-frontend` để push thư mục backend.

Frontend và Backend nên dùng 2 repository riêng:

```text
meeting-room-frontend
meeting-room-backend
```
