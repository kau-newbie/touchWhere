package com.mytutor.touchwhere.di
import dagger.hilt.android.components.ServiceComponent
import dagger.Module
import dagger.Provides
import dagger.hilt.*
import com.mytutor.touchwhere.util.BubbleStateChangeable
import android.app.Service

@Module
@InstallIn(ServiceComponent::class) // 서비스 범위에 설치
object OverlayServiceModule {

    @Provides
    fun provideBubbleStateChangeable(service: Service): BubbleStateChangeable {
        // OverlayService가 BubbleStateChangeable을 상속받고 있어야 합니다.
        // Hilt는 현재 실행 중인 서비스를 'Service' 타입으로 제공할 수 있습니다.
        return service as BubbleStateChangeable
    }
}