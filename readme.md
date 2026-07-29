# **카톡튜터 (가제) 프로젝트**

## **Operation Flow**

#### 목표 설정 모드

사용자로부터 음성인식을 통해 최종 목표를 인식하고, 그 직후 LLM의 응답을 받기 까지의 과정.

![first_operation_flow](./prj-androidtutor-basicmodel.jpg)

이미 목표는 받았고, 목표달성을 판단하기까지 반복되는 과정.

![after_operation_flow](./반복로직.drawio.png)

---

## **To Do List**

#### **Frontend**

all activity & xm
- [x] main, splash, settings pages
- [x] exit button, setting button 구현
- [x] 녹음 버튼이자 오버레이로 띄우는 button
    - [x] 버튼 누르면 녹음 후, whisper에게 보내고, 응답 받기
    - [x] 응답을 특정 버퍼에 저장 <= 이후 제미나이 호출까지 논스톱 파이프라인 만들기
    - [x] 오버레이 버튼 움직일 수 있게 하기
    - [x] 오버레이 버튼과 통합

#### **resrouces**

- [x] imgs
- [x] animations
- [x] better prompts

#### **background**

- [x] overlay 구현하기
- [x] prompt 작성 / api 호출 
- [x] api를 토대로 애니메이션 구현

#### **Inner Logic**

- [x] understand how does LLM api work
- [x] api 호출 테스트
    - [x] gpt4o : 최종 결정/지시 담당
    - [x] whisper : 음성파일을 텍스트로 바꿔주는 담당

#### **Test**

- [x] 전체 과정 테스트(kakaotalk)
    - [x] 1차 테스트(간단한 "hi" 메시지 보내기)
    - [x] 2차 테스트(복잡한 메시지 전송 및 기본 프로필로 바꾸기)
    - [x] 3차 테스트(찾는 대상이 없을 때 - 사람, 앱)
    - [x] 최종 테스트: 1,2,3차 테스트 20번

- [x] 최종 테스트 (결과: 9/32)

---



