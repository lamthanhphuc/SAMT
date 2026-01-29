# **SOFTWARE REQUIREMENTS SPECIFICATION (SRS)**

## **Project: SWP391 Academic Management Tool (SAMT)**

**Version:** 1.2

**Date:** January 13, 2026

**Status:** Final Version

---

## **1\. GIỚI THIỆU (INTRODUCTION)**

### **1.1 Mục tiêu (Purpose) \[CLO1\]**

Tài liệu này xác định các yêu cầu kỹ thuật và nghiệp vụ cho hệ thống **SAMT**. Hệ thống giúp tự động hóa việc tổng hợp dữ liệu từ Jira và GitHub để hỗ trợ sinh viên tạo tài liệu SRS chuẩn và giúp Giảng viên theo dõi tiến độ thực tế của dự án.

### **1.2 Phạm vi (Scope) \[CLO1\]**

* **Hệ thống:** Ứng dụng Web/Mobile tích hợp AI.  
* **Tích hợp:** Kết nối trực tiếp với Jira Software API và GitHub API.  
* **Đầu ra:** Báo cáo tiến độ, chỉ số đóng góp cá nhân và file đặc tả SRS.  
  ---

  ## **2\. MÔ TẢ TỔNG QUAN (OVERALL DESCRIPTION)**

  ### **2.1 Kiến trúc hệ thống \[CLO2\]**

Hệ thống triển khai theo mô hình **Microservices** trên nền tảng **Docker**, bao gồm:

1. **Identity Service:** Quản lý xác thực OAuth2 (Google/GitHub).  
2. **Sync Service:** Đảm nhận việc crawl dữ liệu từ Jira/GitHub.  
3. **AI Analysis Service:** Phân tích ngôn ngữ tự nhiên và chất lượng code.  
4. **Reporting Service:** Xuất dữ liệu ra các định dạng văn bản học thuật.

   ### **2.2 Các tác nhân (User Classes) \[CLO7\]**

* **Admin:** Cấu hình hệ thống và quản lý danh sách lớp học.  
* **Lecturer:** Giám sát, đánh giá chất lượng và chấm điểm dựa trên dữ liệu thực tế.  
* **Team Leader:** Quản lý yêu cầu nhóm, phân công và xuất file SRS.  
* **Team Member:** Theo dõi task cá nhân và xem đánh giá đóng góp.  
  ---

  ## **3\. YÊU CẦU CHỨC NĂNG (FUNCTIONAL REQUIREMENTS) \[CLO1\]**

  ### **3.1 TỔNG QUAN BIỂU ĐỒ USE CASE**

Hệ thống bao gồm 4 tác nhân chính tương tác với các nhóm chức năng: Quản trị hệ thống, Quản lý đào tạo, Quản lý dự án nhóm và Theo dõi cá nhân.

---

### **3.2 DANH SÁCH USE CASE THEO TỪNG VAI TRÒ (ROLES)**

#### **3.2.1 Nhóm chức năng dành cho Admin (Quản trị viên)**

* **UC01: Quản lý người dùng:** Thêm, sửa, xóa thông tin Sinh viên và Giảng viên.

  ## **Software Requirements Specification – Identity Service**

  ---

  ## **UC01 – User Login**

  ### **Description**

Cho phép người dùng đăng nhập hệ thống bằng email và mật khẩu.

---

### **Primary Actor**

* User

  ---

  ### **Preconditions**

* User đã tồn tại trong hệ thống

* status \= ACTIVE

  ---

  ### **Main Flow**

1. User gửi request login với email và password

2. System kiểm tra user tồn tại

3. System kiểm tra status

4. System verify password

5. System sinh access token & refresh token

6. System trả response thành công

   ---

   ### **Alternate Flows**

* **User không tồn tại** → Authentication failed

* **Password sai** → Authentication failed

* **Status \= INACTIVE | LOCKED** → Access denied

  ---

  ### **Response (Success)**

  {  
    "accessToken": "jwt-token",  
    "refreshToken": "refresh-token",  
    "expiresIn": 900  
  }  
    
  ---

  ### **Postconditions**

* Refresh token được lưu DB

* Access token được dùng cho các request tiếp theo  
    
* **UC02: Quản lý nhóm sinh viên:** Tạo nhóm dự án cho môn học SWP391.  
* **UC03: Phân công Giảng viên:** Gán Giảng viên hướng dẫn (Supervisor) cho từng nhóm dự án cụ thể.  
* **UC04: Cấu hình tích hợp hệ thống:** Thiết lập thông số API toàn cục, quản lý các kết nối OAuth với Jira Software và GitHub.

  #### **3.2.2 Nhóm chức năng dành cho Lecturer (Giảng viên)**

* **UC05: Xem danh sách nhóm phụ trách:** Quản lý thông tin và thành viên của các nhóm được phân công hướng dẫn.  
* **UC06: Theo dõi yêu cầu và công việc:** Xem danh sách Epics, Stories và Tasks của nhóm từ Jira.  
* **UC07: Xem báo cáo tiến độ dự án:** Theo dõi biểu đồ Burndown chart và tốc độ hoàn thành công việc của nhóm.  
* **UC08: Xem thống kê đóng góp GitHub:** Xem báo cáo chi tiết về tần suất commit, số dòng code (LOC) và chất lượng mã nguồn của từng sinh viên.  
* **UC09: Đánh giá chất lượng đóng góp:** Hệ thống hỗ trợ chấm điểm dựa trên dữ liệu thực tế từ Jira và GitHub.

  #### **3.2.3 Nhóm chức năng dành cho Team Leader (Trưởng nhóm)**

* **UC10: Đồng bộ dữ liệu dự án:** Kết nối và lấy dữ liệu yêu cầu từ Jira, dữ liệu mã nguồn từ GitHub về hệ thống SAMT.  
* **UC11: Quản lý yêu cầu nhóm:** Ánh xạ (Map) các Jira Issues vào các mục tương ứng trong cấu trúc tài liệu SRS.  
* **UC12: Tạo tài liệu SRS tự động:** Hệ thống tổng hợp dữ liệu để xuất file SRS theo chuẩn IEEE 830/29148.  
* **UC13: Phân công công việc:** Gán Task cho các thành viên (dữ liệu được đồng bộ hai chiều với Jira).  
* **UC14: Xem báo cáo tổng hợp nhóm:** Theo dõi hiệu suất làm việc của toàn team để điều chỉnh kế hoạch.

  #### **3.2.4 Nhóm chức năng dành cho Team Member (Thành viên)**

* **UC15: Xem danh sách công việc cá nhân:** Theo dõi các Task được giao trên giao diện tập trung của SAMT.  
* **UC16: Cập nhật trạng thái công việc:** Thay đổi trạng thái Task (To-do, In Progress, Done) – đồng bộ ngược lại Jira.  
* **UC17: Xem thống kê cá nhân:** Theo dõi chỉ số commit cá nhân và mức độ đóng góp so với trung bình của nhóm.

#### **3.2.5 Nhóm chức năng dùng chung (Common Use Cases)**

* **UC18: Đăng nhập (Login):** Xác thực thông qua tài khoản Google hoặc tài khoản nội bộ.  
* **UC19: Quản lý thông tin cá nhân:** Cập nhật email, liên kết tài khoản Jira ID và GitHub Username.

---

### **3.3 MÔ TẢ USE CASE DIAGRAM (PLANTUML)**  **![][image1]**

https://app.diagrams.net/\#G1dIYlz7NYFMqShsOqYqw3pEyXYfOpgjYT\#%7B%22pageId%22%3A%22t-SDqpFQ4dpL-EcVTPzM%22%7D

---

## **4\. YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS) \[CLO2\]**

* **Hiệu năng:** Phản hồi truy vấn dữ liệu báo cáo trong vòng \< 5 giây.  
* **Bảo mật:** Mã hóa API Keys của Jira/GitHub bằng chuẩn AES-256.  
* **Khả dụng:** Giao diện tối ưu hóa cho cả trình duyệt desktop và thiết bị di động.  
  ---

Dựa trên tài liệu SRS đã cung cấp, dưới đây là nội dung mở rộng chi tiết cho **Mục 5: THIẾT KẾ KIẾN TRÚC VÀ MÔ HÌNH PHÂN TÍCH**, tập trung vào việc áp dụng các Design Patterns để giải quyết các bài toán kỹ thuật cụ thể của hệ thống SAMT.

---

## **5\. THIẾT KẾ KIẾN TRÚC VÀ MÔ HÌNH PHÂN TÍCH \[CLO3\]**

Việc thiết kế hệ thống SAMT tuân theo kiến trúc Microservices đòi hỏi sự phối hợp chặt chẽ giữa các thành phần để đảm bảo tính linh hoạt, khả năng bảo trì và hiệu năng. Phần này trình bày chi tiết việc áp dụng các mẫu thiết kế (Design Patterns) tiêu chuẩn để giải quyết các vấn đề về khởi tạo đối tượng, cấu trúc hệ thống và hành vi tương tác giữa các module.

### **5.1 Áp dụng Design Patterns \[CLO3\]**

#### **A. Nhóm Creational Patterns (Mẫu Khởi tạo)**

Nhóm này giải quyết các vấn đề liên quan đến việc khởi tạo đối tượng, giúp hệ thống độc lập với cách thức các đối tượng được tạo ra, cấu thành và biểu diễn.

**1\. Singleton Pattern (Đơn bản)**

* **Áp dụng:** Class DatabaseConnector trong module quản lý dữ liệu.  
* **Mô tả kỹ thuật:**  
  * Hệ thống SAMT sử dụng cơ sở dữ liệu quan hệ PostgreSQL để lưu trữ dữ liệu đồng bộ từ Jira và GitHub. Việc thiết lập kết nối đến cơ sở dữ liệu là một thao tác tốn kém tài nguyên và thời gian.  
  * DatabaseConnector được thiết kế với một private static instance và một private constructor. Phương thức getInstance() đảm bảo rằng tại bất kỳ thời điểm nào, toàn bộ ứng dụng chỉ tồn tại duy nhất một thể hiện của lớp kết nối này.  
* **Lợi ích trong SAMT:**  
  * **Quản lý tài nguyên:** Ngăn chặn việc tạo tràn lan các kết nối thừa thãi gây quá tải cho PostgreSQL server.  
  * **Tính nhất quán:** Đảm bảo tất cả các luồng xử lý (threads) khi truy xuất dữ liệu Users hay Groups đều đi qua một điểm kiểm soát cấu hình duy nhất (Connection Pool).

**2\. Factory Method Pattern (Phương thức nhà máy)**

* **Áp dụng:** Module ExportService phục vụ chức năng xuất báo cáo.  
* **Mô tả kỹ thuật:**  
  * Hệ thống yêu cầu đầu ra là các định dạng văn bản học thuật khác nhau như file SRS (Word/Docx) hoặc báo cáo tiến độ (PDF).  
  * Interface IReportExporter định nghĩa phương thức chung exportReport().  
  * Lớp ReportFactory sẽ dựa trên tham số đầu vào từ người dùng (loại báo cáo: SRS hay Progress) để quyết định khởi tạo đối tượng PDFExporter hay DocxExporter tương ứng.  
* **Lợi ích trong SAMT:**  
  * **Tính mở rộng (Open/Closed Principle):** Nếu trong tương lai Giảng viên yêu cầu xuất báo cáo dạng Excel (.xlsx) hoặc Markdown (.md), lập trình viên chỉ cần tạo thêm class mới hiện thực IReportExporter mà không cần sửa đổi mã nguồn cốt lõi của chức năng xuất file.

  ---

  #### **B. Nhóm Structural Patterns (Mẫu Cấu trúc)**

Nhóm này tập trung vào việc tổ chức các lớp và đối tượng để tạo nên các cấu trúc lớn hơn, giúp tích hợp các hệ thống con khác biệt.

**1\. Adapter Pattern (Người chuyển đổi)**

* **Áp dụng:** Tầng tích hợp Sync Service kết nối với Jira và GitHub.  
* **Mô tả kỹ thuật:**  
  * Jira cung cấp dữ liệu qua REST API với cấu trúc JSON phức tạp (fields, changelogs). GitHub cung cấp dữ liệu qua GraphQL hoặc REST API (commits, stats).  
  * Cấu trúc dữ liệu nội bộ của SAMT đã được chuẩn hóa trong các bảng Jira\_Issues và Github\_Commits.  
  * Lớp JiraAdapter và GithubAdapter đóng vai trò trung gian, chuyển đổi ("adapt") dữ liệu thô từ API của bên thứ ba thành các đối tượng Issue và Commit đúng chuẩn entity của SAMT.  
* **Lợi ích trong SAMT:**  
  * **Độc lập hệ thống:** Nếu Jira hoặc GitHub thay đổi phiên bản API, chỉ cần sửa đổi logic trong lớp Adapter. Các module xử lý nghiệp vụ như tính điểm hay tạo SRS không bị ảnh hưởng.

**2\. Facade Pattern (Mặt tiền)**

* **Áp dụng:** Lớp ProjectSummaryFacade phục vụ cho Frontend Dashboard.  
* **Mô tả kỹ thuật:**  
  * Để hiển thị "Báo cáo tổng hợp nhóm", Frontend cần dữ liệu từ nhiều nguồn: thông tin thành viên (Identity Service), tiến độ Task (Sync Service), và điểm chất lượng code (AI Analysis Service).  
  * Thay vì Frontend phải gọi 3-4 API riêng lẻ, ProjectSummaryFacade cung cấp một giao diện đơn giản hóa: getProjectOverview(groupID). Lớp Facade này sẽ tự động gọi đến các microservices cần thiết, tổng hợp dữ liệu và trả về một kết quả duy nhất.  
* **Lợi ích trong SAMT:**  
  * **Tối ưu hiệu năng mạng:** Giảm số lượng request từ client đến server.  
  * **Ẩn giấu sự phức tạp:** Frontend không cần biết logic lấy dữ liệu phân tán nằm ở đâu, chỉ cần giao tiếp với Facade.

  ---

  #### **C. Nhóm Behavioral Patterns (Mẫu Hành vi)**

Nhóm này giải quyết các vấn đề về phân công trách nhiệm và giao tiếp giữa các đối tượng, giúp luồng điều khiển linh hoạt hơn.

**1\. Observer Pattern (Người quan sát)**

* **Áp dụng:** Hệ thống thông báo (Notification System) dành cho Giảng viên.  
* **Mô tả kỹ thuật:**  
  * **Subject (Chủ thể):** SyncService \- nơi theo dõi hoạt động commit code và tiến độ Jira.  
  * **Observer (Người quan sát):** NotificationService.  
  * Cơ chế hoạt động: Khi SyncService phát hiện một sự kiện đặc biệt (ví dụ: sinh viên commit \> 1000 dòng code/lần, hoặc trạng thái Sprint chuyển sang "Done"), nó sẽ kích hoạt phương thức notifyObservers().  
  * NotificationService nhận tín hiệu và gửi cảnh báo đến Giảng viên (Lecturer) qua email hoặc thông báo trên ứng dụng.  
* **Lợi ích trong SAMT:**  
  * **Phản ứng thời gian thực:** Giảng viên nắm bắt ngay lập tức các hành vi bất thường hoặc các mốc quan trọng của dự án mà không cần phải vào hệ thống kiểm tra thủ công liên tục.  
  * **Lỏng lẻo (Loose Coupling):** Module đồng bộ dữ liệu không cần biết chi tiết về cách gửi email/thông báo, chỉ cần phát đi sự kiện.

**2\. Strategy Pattern (Chiến lược)**

* **Áp dụng:** Module Đánh giá và Chấm điểm (Grading Service).  
* **Mô tả kỹ thuật:**  
  * Mỗi Giảng viên có quan điểm chấm điểm khác nhau. Interface IGradingStrategy được định nghĩa với phương thức calculateScore().  
  * Hệ thống triển khai các chiến lược cụ thể:  
    * TaskVolumeStrategy: Chấm điểm dựa trên số lượng Task hoàn thành và Story Points (Dữ liệu Jira).  
    * CodeComplexityStrategy: Chấm điểm dựa trên độ khó của code và chỉ số AI Quality Score (Dữ liệu GitHub).  
  * Tại thời điểm chạy (runtime), Giảng viên có thể lựa chọn chiến lược chấm điểm mong muốn thông qua cấu hình, và Context sẽ chuyển đổi thuật toán xử lý tương ứng.  
* **Lợi ích trong SAMT:**  
  * **Tính linh hoạt cao:** Đáp ứng đa dạng nhu cầu đánh giá của Giảng viên mà không cần viết lại code logic nghiệp vụ (Business Logic).

### 

  ---

  ## **6\. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE DESIGN) \[CLO4\]**

#### Hệ thống **SAMT** sử dụng hệ quản trị cơ sở dữ liệu quan hệ (RDBMS) là **PostgreSQL**. Thiết kế dưới đây tuân thủ chuẩn hóa mức 3 (3NF) để giảm thiểu dư thừa dữ liệu và đảm bảo tính nhất quán khi đồng bộ từ nhiều nguồn (Jira, GitHub).

### **6.1 Các thực thể chính (Entities)**

#### Dựa trên phân tích nghiệp vụ, các thực thể dữ liệu chính bao gồm:

1. #### **Users (Người dùng):** Quản lý thông tin định danh của Admin, Giảng viên và Sinh viên.

2. #### **Groups (Nhóm dự án):** Đơn vị quản lý chính của lớp học phần SWP391.

3. #### **Project\_Configs (Cấu hình dự án):** Lưu trữ thông tin nhạy cảm về kết nối API.

4. #### **Jira\_Issues (Yêu cầu/Công việc):** Bản sao dữ liệu các Ticket từ Jira.

5. #### **Github\_Commits (Mã nguồn):** Dữ liệu lịch sử commit và các chỉ số phân tích.

6. #### **Reports (Báo cáo):** Lưu trữ các file SRS hoặc báo cáo đã được hệ thống tạo ra.

   ### **6.2 Lược đồ Quan hệ Thực thể (ERD \- PlantUML)**

#### Dưới đây là mã nguồn để sinh lược đồ ERD minh họa mối quan hệ giữa các bảng:

![][image2]

#### **6.3.1 Bảng Users**

Lưu trữ thông tin người dùng hệ thống. Bảng này đóng vai trò trung tâm để ánh xạ (map) giữa tài khoản sinh viên FPT và tài khoản trên Jira/GitHub.

| Tên thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :---- | :---- | :---- | :---- |
| **user\_id** | UUID | **PK** | Định danh duy nhất của người dùng. |
| email | VARCHAR(255) | UNIQUE, NOT NULL | Email đăng nhập (sử dụng OAuth Google). |
| full\_name | VARCHAR(100) | NOT NULL | Tên hiển thị đầy đủ. |
| role | VARCHAR(20) | CHECK (Admin, Lecturer, Student) | Phân quyền truy cập hệ thống. |
| jira\_account\_id | VARCHAR(100) | NULL | ID tài khoản Jira (dùng để map assignee). |
| github\_username | VARCHAR(100) | NULL | Username GitHub (dùng để map author). |

#### **6.3.2 Bảng Groups**

Quản lý thông tin nhóm dự án (Capstone/SWP Project).

| Tên thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :---- | :---- | :---- | :---- |
| **group\_id** | UUID | **PK** | Định danh nhóm. |
| group\_name | VARCHAR(100) | NOT NULL | Tên nhóm (VD: SE1705-G1). |
| semester | VARCHAR(20) | NOT NULL | Học kỳ (VD: Spring2026). |
| **lecturer\_id** | UUID | **FK** (Ref Users.user\_id) | Giảng viên hướng dẫn (Supervisor). |
| created\_at | TIMESTAMP | DEFAULT NOW() | Thời điểm tạo nhóm. |

#### **6.3.3 Bảng Group\_Members (Bảng nối)**

Giải quyết mối quan hệ N-N giữa Sinh viên và Nhóm, đồng thời xác định vai trò Leader.

| Tên thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :---- | :---- | :---- | :---- |
| **group\_id** | UUID | **PK, FK** (Ref Groups) | Thuộc về nhóm nào. |
| **student\_id** | UUID | **PK, FK** (Ref Users) | Sinh viên nào. |
| is\_leader | BOOLEAN | DEFAULT FALSE | Xác định TRUE nếu là Trưởng nhóm. |

#### **6.3.4 Bảng Project\_Configs**

Lưu trữ cấu hình tích hợp. Tách riêng bảng này để tăng cường bảo mật cho các Token API (có thể mã hóa cột token).

| Tên thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :---- | :---- | :---- | :---- |
| **config\_id** | UUID | **PK** | Định danh cấu hình. |
| **group\_id** | UUID | **FK**, UNIQUE | Mỗi nhóm chỉ có 1 cấu hình tích hợp. |
| jira\_host\_url | VARCHAR(255) | NOT NULL | URL dự án Jira (VD: projects.atlassian.net). |
| jira\_api\_token | TEXT | NOT NULL | Token xác thực Jira (Encrypted). |
| github\_repo\_url | VARCHAR(255) | NOT NULL | URL GitHub Repository. |
| github\_token | TEXT | NOT NULL | Token xác thực GitHub (Encrypted). |

#### **6.3.5 Bảng Jira\_Issues**

Lưu trữ dữ liệu đồng bộ từ Jira. Đây là nguồn dữ liệu chính để tạo SRS (User Stories) và Dashboard (Tasks).

| Tên thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :---- | :---- | :---- | :---- |
| **issue\_key** | VARCHAR(50) | **PK** | Key định danh trên Jira (VD: SWP-123). |
| **group\_id** | UUID | **FK** (Ref Groups) | Thuộc dự án nào. |
| summary | TEXT | NOT NULL | Tiêu đề Issue/Requirement. |
| description | TEXT | NULL | Mô tả chi tiết (Input cho SRS). |
| issue\_type | VARCHAR(20) | NOT NULL | Epic, Story, Task, Bug. |
| status | VARCHAR(50) | NOT NULL | To Do, In Progress, Done. |
| story\_points | INT | DEFAULT 0 | Điểm độ phức tạp. |
| **assignee\_id** | UUID | **FK** (Ref Users) | Người được giao việc (map qua jira\_account\_id). |

#### **6.3.6 Bảng Github\_Commits**

Lưu trữ dữ liệu đồng bộ từ GitHub và kết quả phân tích từ Module AI.

| Tên thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :---- | :---- | :---- | :---- |
| **commit\_hash** | VARCHAR(40) | **PK** | Mã SHA-1 của commit. |
| **group\_id** | UUID | **FK** (Ref Groups) | Thuộc dự án nào. |
| **author\_id** | UUID | **FK** (Ref Users) | Người commit (map qua github\_username). |
| message | TEXT | NOT NULL | Nội dung commit message. |
| additions | INT | DEFAULT 0 | Số dòng code thêm vào. |
| deletions | INT | DEFAULT 0 | Số dòng code xóa đi. |
| commit\_date | TIMESTAMP | NOT NULL | Thời gian commit. |
| ai\_quality\_score | DECIMAL(4,2) | NULL | Điểm chất lượng code (0-10) do AI chấm. |
| ai\_analysis | TEXT | NULL | Nhận xét tự động của AI về commit này. |

#### **6.3.7 Bảng Reports**

Lưu trữ lịch sử các báo cáo đã tạo để Giảng viên/Sinh viên tải lại khi cần.

| Tên thuộc tính | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :---- | :---- | :---- | :---- |
| **report\_id** | UUID | **PK** | Định danh báo cáo. |
| **group\_id** | UUID | **FK** (Ref Groups) | Báo cáo của nhóm nào. |
| type | VARCHAR(20) | CHECK (SRS, Progress) | Loại báo cáo. |
| file\_path | VARCHAR(255) | NOT NULL | Đường dẫn file (trên Server/Cloud Storage). |
| **created\_by** | UUID | **FK** (Ref Users) | Người yêu cầu tạo báo cáo (Leader). |
| created\_at | TIMESTAMP | DEFAULT NOW() | Thời gian tạo. |

### **6.4 Phân tích mối quan hệ (Relationships)**

1. **Users \- Groups (1 \- N):** Một Giảng viên có thể hướng dẫn nhiều Nhóm (Groups.lecturer\_id).  
2. **Users \- Groups (N \- N):** Một Sinh viên có thể thuộc một Nhóm (được giải quyết qua bảng trung gian Group\_Members).  
3. **Groups \- Project\_Configs (1 \- 1):** Một Nhóm có duy nhất một bộ cấu hình API.  
4. **Groups \- Jira\_Issues (1 \- N):** Một Nhóm có nhiều Task/Story trên Jira.  
5. **Groups \- Github\_Commits (1 \- N):** Một Nhóm có nhiều Commit mã nguồn.  
6. **Users \- Jira\_Issues (1 \- N):** Một Sinh viên được assign nhiều Task.  
7. **Users \- Github\_Commits (1 \- N):** Một Sinh viên thực hiện nhiều Commit.  
     
   ---

   ## **7\. ỨNG DỤNG AI VÀ CẢI TIẾN \[CLO5\]**

* **AI Requirement Polishing:** Tự động sửa lỗi ngữ pháp và định dạng cho các yêu cầu phần mềm để đảm bảo tính chuyên nghiệp trong file SRS.  
* **Predictive Analytics:** Sử dụng máy học (Machine Learning) để dự báo ngày hoàn thành dự án dựa trên tốc độ làm việc hiện tại của nhóm.
