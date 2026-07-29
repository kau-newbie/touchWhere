package com.mytutor.touchwhere.data.dto

// class선언일 뿐이라, 실제는 instance로 받아올 때 가져올 수 있다.
// server로부터 데이터를 json으로 받아오게 되는데, 이걸 parsing한 다음, text 부분의 값만 가져와요. String으로.
data class AttResponse( //data keyword를 통해서
    val text: String
    // OpenAI는 실제로는 더 많은 정보를 주지만, 지금은 텍스트만.
)