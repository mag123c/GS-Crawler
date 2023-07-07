# GS-Crawler

## 📝설명
Window, Linux 환경에서의JDBC, selenium을 이용한 키워드 검색 결과 데이터를 DB에 insert하고, update할 수 있는 Crawler

+ (07/04) 자동화 시 reCAPTCHA의 100% 회피가 불가능하여, parsing만 해주는 반자동화 Crawler 제작.

고도화 기간 : 2023.05 ~ 2023.06
<br><br>


## 💡인계사항
1. Crawler App 내부에서 모든 동작 수행(DB connection, Logging)
2. 24시간 기준, 일 데이터 insert **100건 내외**
<br><br>

## ⏫고도화
1. 모듈단위의 분리 (DB Connection, Common Configuration, Logging)
2. Crawler의 원활한 구동을 위한 여러 시도들
   - random term 적용.
   - routing table 조작(NIC Switching)
   - seleinum 실행 로직 변경
3. (07/04) 데이터의 필요성에 초점을 맞춰 새로운 아이디어 제시
   - 데이터가 필요하다는 관점에서, 필요 시 최소한의 동작을 사람이 수행하고 프로그램이 데이터 파싱 및 insert/update 수행하자!!!
   - 사용자가 검색어 입력과 페이지 이동의 과정은 직접 수행. 나머지 작업은 프로그램이 수행.
<br><br>

## 👍성과
1. 모듈단위 분리를 통한 App 실행 시 DB 내의 search PK, Crawling할 페이지 수를 argument로 받음.
2. reCAPTCHA 및, 429 error, 403 error handling 시도 ➡️ **일 단위 insert data 최소 4배 증가 (100 -> 400건 이상)**
3. (07/04) 새로운 프로그램 제작으로 insert data의 증가 ➡️ **(일 단위 400건 이상 -> 10분 당 450건 이상)**
<br><br>

## 😡한계
Google의 주기적인 봇 탐지, Crawler block 기능이 update되어 현 상황에 대한 단발적인 해결책만 제시 가능하고, 주기적인 update가 필요하여, 자동화라고 된 Crawler라고 보기 어려움
(07/04) 사용자가 직접 검색어 입력, 페이지 넘김 등의 최소 작업이 필요
<br><br>

## 🛠️기술
1. Java 8, JDBC
2. Library - jsoup, mysql connect-J, selenium, webdrivermanager
<br><br>

## 사용

### 자동화 Crawler
```
//system args 설정
java -jar [filename] [DB keyword ID(PK)] [max_page_num]
```
1. DB, Log, 설정 경로를 본인의 경로로 셋팅 필수
2. 관리자 권한으로의 실행 필요(NIC Switching)
3. NIC Switching을 미사용 시, switchingNIC 메서드와 관련 작업 수행 코드 주석처리.
4. gateway map에 실 사용되는 gateway 입력

### 반자동화 Crawler
1. 단순 Application Run
2. 실행되는 Chrome에 검색어 입력
3. Parsing 완료 후 페이지 넘김
4. 반복
5. Chrome 종료 시 Application Terminate
