# GS-Crawler

## 개요

#### 기존 Crawler
1. Crawler App 내부에서 모든 동작 수행(DB connection, Logging)
2. 24시간 기준, 일 데이터 insert 100건 내외

#### 고도화
1. 모듈단위의 분리 (DB Connection, Common Configuration, Logging)
2. Crawler의 원활한 구동을 위한 여러 시도들
   - random term 적용.
   - routing table 조작(NIC Switching)
   - seleinum 실행 로직 변경
  
#### 성과
1. 모듈단위 분리를 통한 App 실행 시 DB 내의 search PK, Crawling할 페이지 수를 argument로 받음.
2. reCAPTCHA 및, 429 error, 403 error handling 시도 -> 일 insert 최소 400건

#### 한계
Google의 주기적인 봇 탐지, Crawler block 기능이 update되어 현 상황에 대한 단발적인 해결책만 제시 가능하고, 주기적인 update가 필요하여, 자동화라고 된 Crawler라고 보기 어려움

#### technic
1. Java 8, JDBC
2. Library - jsoup, mysql connect-J, selenium, webdrivermanager

<hr>

## 사용하기

```
java -jar [filename] [DB keyword ID(PK)] [max_page_num]
```
1. DB, Log, 설정 경로를 본인의 경로로 셋팅
2. 관리자 권한으로의 실행 필요(NIC Switching)

