## 목표: 알림 대응 패턴 파악 (클릭, 제거, 무시) & 커스터마이징 테스트

<br/>1. Notification Listener Service API 사용
<br/>- 사용자가 어떤 알림을 언제, 어떻게 제거하는지 추적 가능 - (예: 스와이프해서 제거, 모두 지우기 버튼, pc로 접속 등)
<br/>
<br/>2. 무시되는 알림(처리하지 않는 알림)을 opportune moment에 전송해주는 서비스 추가
<br/>- 무시되는 알림이란, 사용자가 상단바를 내려서 봤는데도 클릭하거나 제거하지 않은 알림
<br/>- 무시되는 알림 중 나중에 처리하기 위해 알림을 쌓아 두었다가 놓치는 경우를 방지

<br/>-단 하나의 화면 UI만 제공하며, 서비스를 활성화 시키는 notification 버튼 1개만 존재
<br/> <img src=https://github.com/user-attachments/assets/ecc1647a-669e-47a6-8d9c-0c7362396094 width="300" height="500"/>
<br/>
<br/>알림이 오면, 사용자는 상단바를 내려서 알림을 확인함
<br/>사용자는 두가지 행동 양식 : 1)알림을 처리(클릭or제거)하거나 2)알림을 무시
<br/>
<br/>무시된 알림은 사용자가 화면을 끌 때, 
<br/>알림을 삭제하고 저장한 후, UXChannel Service를 통해 알림을 재전송
<br/> <img src=https://github.com/user-attachments/assets/6af67fdd-6ead-4057-a364-66f1fe74c520 width="900" height="200"/>
<br/>
<br/>1. 무시된 알림은 다음 휴대폰 화면을 켤 때 전송 시작
<br/>: 알림 수용률이 제일 높은 타이밍은 휴대폰을 사용중일 때 [34]
<br/>2. 사용중인 앱이 종료될때마다 저장되어 있던 알림을 1개씩 전송 
<br/>: 모바일과 상호작용이 제일 적은 시간인 앱의 전환시점을 opportune moment로 정의
<br/>3. 사용자가 화면을 끌 때까지 재전송된 알림을 처리(클릭or제거)하지 않는다면 다시 저장
<br/> <img src=https://github.com/user-attachments/assets/3359f87e-db2d-4915-a5aa-08e255e0c87b width="600" height="200"/>
<br/>
<br/> 실험 수집 데이터
<br/> <img src=https://github.com/user-attachments/assets/1e002e61-e741-4acb-ad6a-6bc8a1f1c15c width="500" height="300"/>
<br/>인터뷰 주요 내용
<br/> #장점
<br/>신경 끄고 있던 알림이 다시 리마인드 됨
<br/>놓친 알림을 확인 할 수 있었음
<br/>재전송 알림이 알림을 확인할 수 있을 때 전송
<br/> #단점
<br/>전송될 때마다 확인하는 것이 아니기에 알림이  쌓임
<br/>클릭하지 않아도 상단바에서 확인해야하는 것(예: 날씨)들도 숨겨진 경우가 있음
<br/>리마인드 된 알림이 계속 전송이 되어서 불편
<br/> <img src=https://github.com/user-attachments/assets/f7fef6ec-1437-4cc7-8d40-59b8438c5b67 width="500" height="300"/>
<br/>알림 처리 시간과 인터뷰 내용을 확인하면, 사용자마다 결과가 다 다르다는 것을 확인 
<br/>이는 사용자의 다양한 처리 유형을 고려하지 못한 결과임을 시사. 이를 통해, 개인화 가능성 및 필요성 파악
<br/>
<br/>시스템 활성화 전/후의 클릭 비율이 사용자마다 다름
<br/>사용자가 자신에게 중요도가 높은 알림을 주로 클릭
<br/>사용자마다 처리되지 않은 알림이 중요한 알림일수도 아닐수도 있음을 시사 
<br/> <img src=https://github.com/user-attachments/assets/31ea2e2a-b6e8-4e22-83ed-50b335ee24f0 width="500" height="300"/>
<br/>
<br/>통계적으로 앱별 평균 알람 확인 시간에 차이가 있음을 확인 (p<.01)
<br/>User1의 평균 카카오톡 메시지 알람 확인 시간: 20분 
<br/>User1의 평균 유튜브 알람 메시지 확인 평균 시간: 45분
<br/>알림의 처리시간이 짧다는 것은 해당 앱의 알림이 중요도가 높다는 것을 시사함
<br/>각 사용자는 앱마다 처리시간이 다르다는 것을 확인. 이를 통해 앱마다 전송 시간을 다르게 설정하는 것이 효율적임을 시사
<br/> <img src=https://github.com/user-attachments/assets/8a8f85a3-77d7-4ab8-9225-f2dd7ef66529 width="500" height="300"/>