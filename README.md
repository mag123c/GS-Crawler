# GS-Crawler

## 설명
Window, Linux 환경에서의JDBC, selenium을 이용한 키워드 검색 결과 데이터를 DB에 insert하고, update할 수 있는 Crawler
고도화 기간 : 2023.05 ~ 2023.06
<br><br>

## 인계 시 기존 Crawler
1. Crawler App 내부에서 모든 동작 수행(DB connection, Logging)
2. 24시간 기준, 일 데이터 insert 100건 내외
<br><br>

## 고도화
1. 모듈단위의 분리 (DB Connection, Common Configuration, Logging)
2. Crawler의 원활한 구동을 위한 여러 시도들
   - random term 적용.
   - routing table 조작(NIC Switching)
   - seleinum 실행 로직 변경
<br><br>

## 성과
1. 모듈단위 분리를 통한 App 실행 시 DB 내의 search PK, Crawling할 페이지 수를 argument로 받음.
2. reCAPTCHA 및, 429 error, 403 error handling 시도 -> 일 insert 최소 400건
<br><br>

## 한계
Google의 주기적인 봇 탐지, Crawler block 기능이 update되어 현 상황에 대한 단발적인 해결책만 제시 가능하고, 주기적인 update가 필요하여, 자동화라고 된 Crawler라고 보기 어려움
<br><br>

## 기술
1. Java 8, JDBC
2. Library - jsoup, mysql connect-J, selenium, webdrivermanager
<br><br>

## 사용

```
//system args 설정
java -jar [filename] [DB keyword ID(PK)] [max_page_num]
```
1. DB, Log, 설정 경로를 본인의 경로로 셋팅 필수
2. 관리자 권한으로의 실행 필요(NIC Switching)
3. NIC Switching을 미사용 시, switchingNIC 메서드와 관련 작업 수행 코드 주석처리.
4. gateway map에 실 사용되는 gateway 입력
