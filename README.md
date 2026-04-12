# Poké Champions

寶可夢冠軍賽（Pokémon Champions）資料查詢與隊伍組建工具。

快速查詢冠軍賽可用寶可夢、屬性相剋、招式資訊，並在線上組建你的最強隊伍。

**線上版**：[https://poke-champions.web.app](https://poke-champions.web.app)

---

## 功能特色

### 寶可夢圖鑑
- 瀏覽冠軍賽全可用寶可夢，支援中英文搜尋與屬性篩選
- 查看種族值、EV 配點模擬（66 點上限、單項 32）、性格加成
- 可學招式清單與屬性防禦相性分析

### 招式查詢
- 依屬性、分類（物理／特殊／變化）篩選招式
- 招式詳情含威力、命中、PP、繁中描述
- 反向查詢：哪些寶可夢可以學習該招式

### 屬性相剋
- 18 屬性互動表格，支援單屬性與雙屬性組合
- 防禦相性倍率分段顯示（免疫、¼×、½×、1×、2×、4×）

### 隊伍組建
- 6 格隊伍編輯器：選擇寶可夢、配招、EV、性格、持有物
- 隊伍總覽：整隊防禦弱點分析與攻擊屬性覆蓋檢查
- 一鍵匯出隊伍 PNG 圖片分享
- 資料存在瀏覽器 localStorage，無需登入

---

## 技術棧

### 前端
- **Vue 3** + **Vue Router 4** + **Vite 5**
- **Axios**（API 呼叫）
- **html2canvas**（隊伍圖片匯出）
- **Firebase Hosting** 部署

### 後端
- **Spring Boot 4** + **Java 21**
- **Spring Data JPA** + **PostgreSQL**
- **Jsoup**（爬取官方冠軍賽名冊與 Game8 資料）
- **Google Gemini API**（招式與道具描述繁中翻譯）
- **springdoc-openapi**（Swagger UI）
- **Cloud Run** + **Cloud SQL** 部署

---

## 本機開發

### 前置需求

- Node.js 18+
- Java 21+
- Maven 3.9+
- PostgreSQL（建立資料庫 `poke-champions-db`）

### 後端

```bash
cd poke-champions-backend

# 設定環境變數（或直接修改 application.properties）
export SPRING_DATASOURCE_PASSWORD=你的密碼
export GEMINI_API_KEY=你的Gemini_API_Key

mvn spring-boot:run
```

後端預設啟動在 `http://localhost:8080`，Swagger UI 在 `/swagger-ui`。

### 前端

```bash
cd poke-champions-frontend
npm install
npm run dev
```

前端啟動在 `http://localhost:5173`，開發模式下 Vite 會自動將 `/api` 請求代理到後端。

### 資料同步

首次啟動後需透過 API 同步資料（可使用 Swagger UI 操作）：

1. `POST /api/roster/full-sync` — 爬取官方名冊並同步種族值
2. `POST /api/roster/sync-types` — 同步屬性相剋表
3. `POST /api/roster/sync-moves` — 同步招式資料
4. `POST /api/roster/sync-pokemon-moves` — 同步寶可夢可學招式
5. `POST /api/roster/sync-items` — 同步持有物

---

## 環境變數

### 後端

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/poke-champions-db` |
| `SPRING_DATASOURCE_USERNAME` | 資料庫使用者 | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | 資料庫密碼 | — |
| `GEMINI_API_KEY` | Google Gemini API Key（翻譯用） | — |
| `APP_API_KEY` | API 存取金鑰（選用，設定後所有 `/api/**` 需帶 `X-Api-Key` header） | — |
| `APP_CORS_EXTRA_ORIGINS` | 額外允許的 CORS 來源（逗號分隔） | — |

### 後端（GCP profile：`SPRING_PROFILES_ACTIVE=gcp`）

| 變數 | 說明 |
|------|------|
| `GCP_SQL_INSTANCE_CONNECTION_NAME` | Cloud SQL 執行個體連線名稱（`PROJECT:REGION:INSTANCE`） |
| `GCP_SQL_DATABASE` | 資料庫名稱 |
| `GCP_SQL_USER` | Cloud SQL 使用者 |
| `GCP_SQL_PASSWORD` | Cloud SQL 密碼 |

### 前端（建置時）

| 變數 | 說明 |
|------|------|
| `VITE_API_BASE_URL` | 後端 API 根網址（例如 `https://xxx.run.app`），未設定則使用 Vite dev proxy |
| `VITE_API_KEY` | 與後端 `APP_API_KEY` 一致 |

---

## 部署

本專案使用 **Cloud Run**（後端）+ **Cloud SQL**（PostgreSQL）+ **Firebase Hosting**（前端）。

詳見 [DEPLOY-GCP.md](./DEPLOY-GCP.md)。

---

## 專案結構

```
poke-champions/
├── poke-champions-frontend/          # Vue 3 前端
│   ├── src/
│   │   ├── views/                    # 頁面元件
│   │   ├── components/               # 共用元件
│   │   ├── composables/              # 組合式函數（隊伍狀態管理）
│   │   ├── api/                      # API 封裝（axios）
│   │   ├── utils/                    # 工具函數
│   │   ├── constants/                # 常數定義
│   │   ├── data/                     # 靜態對照資料
│   │   └── router/                   # 路由定義
│   ├── firebase.json                 # Firebase Hosting 設定
│   └── .env.production.example       # 生產環境變數範本
│
├── poke-champions-backend/           # Spring Boot 後端
│   ├── src/main/java/.../
│   │   ├── controller/               # REST API（RosterSyncController）
│   │   ├── service/                  # 業務邏輯與資料同步
│   │   ├── entity/                   # JPA 實體
│   │   ├── repository/               # Spring Data 介面
│   │   └── config/                   # CORS、API Key Filter、OpenAPI
│   ├── src/main/resources/
│   │   ├── application.properties    # 本機設定
│   │   └── application-gcp.properties # GCP 部署設定
│   └── Dockerfile                    # 容器化
│
├── DEPLOY-GCP.md                     # GCP 部署指南
└── README.md
```

---

## 免責聲明

本站為**非官方**粉絲作品，與 The Pokémon Company、Nintendo、Game Freak 無關。所有寶可夢相關名稱與圖像為其各自擁有者之商標與著作權。
