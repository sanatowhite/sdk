package io.sanato.appkit.core.net.ws

/**
 * 握手凭证的来源——接口归 `:core-net` 所有,实现由同时引了 `:core-net` 和认证
 * 能力的 Tier-3 桥模块(`:auth-net-hilt`)提供。和 `NetworkMetricsSink` 是完全
 * 同一个套路:两个 Tier-1 之间保持零依赖边。`:core-net` 因此完全不知道
 * Firebase / OAuth / JWT 的存在。
 */
fun interface WebSocketTokenProvider {
    /**
     * @param forceRefresh true 时【必须】绕过任何缓存重新获取。重连流程在遇到
     *   [WebSocketError.HandshakeRejected] 且 code ∈ {401, 403} 时会且仅会
     *   置一次 true。
     * @return null 表示当前无凭证(已登出)——连接会以 [WebSocketError.Unauthenticated]
     *   失败且【不重试】。
     */
    suspend fun token(forceRefresh: Boolean): String?
}
