"""
WebSocket客户端 - D1-D2阶段
使用 websocket-client 库（原生WebSocket协议）
对接Java后端的WebSocketHandler，协议格式：ws://host:port/ws/game?token={token}
"""
import json
import time
import uuid
import threading
from typing import Dict, Any, Optional, Callable
import websocket
from config import BotConfig, default_config
from message_handler import MessageHandler, safe_print


class WebSocketClient:
    """WebSocket客户端 - 封装连接和消息收发（原生WebSocket）"""

    # 默认WebSocket路径
    WS_PATH = "/ws/game"

    def __init__(
        self,
        token: str,
        config: BotConfig = default_config,
        bot_id: str = "Bot"
    ):
        self.token = token
        self.config = config
        self.bot_id = bot_id
        self.ws: Optional[websocket.WebSocket] = None
        self.message_handler = MessageHandler(bot_id)
        self.connected = False
        self.player_id: Optional[int] = None
        self.room_id: Optional[str] = None

        # 用户自定义消息处理回调
        self.on_message_callback: Optional[Callable] = None

        # 后台接收线程
        self._recv_thread: Optional[threading.Thread] = None
        self._running = False
        self._connected_event = threading.Event()

    def _build_url(self) -> str:
        """
        构造原生WebSocket连接URL
        PRD §6.1: ws://host:port/ws/game?token={token}
        """
        host_part = self.config.ws_url
        if not host_part.startswith("ws://") and not host_part.startswith("wss://"):
            host_part = f"ws://{host_part}"
        return f"{host_part}{self.WS_PATH}?token={self.token}"

    def connect(self):
        """建立WebSocket连接"""
        ws_url = self._build_url()

        safe_print(f"[{self.bot_id}] 🔌 正在连接: {ws_url}")
        safe_print(f"[{self.bot_id}] [DEBUG] 进入 connect() 方法")

        try:
            # 初始化事件和状态
            self._connected_event = threading.Event()
            self._running = True

            # 创建原生WebSocket对象
            self.ws = websocket.WebSocket()

            # 建立连接
            safe_print(f"[{self.bot_id}] [DEBUG] 即将 ws.connect()...")
            self.ws.connect(ws_url, timeout=5)
            safe_print(f"[{self.bot_id}] [DEBUG] ws.connect() 返回成功")
            safe_print(f"[{self.bot_id}] [DEBUG] 连接后 ws.connected={self.ws.connected}")
            self.connected = True
            safe_print(f"[{self.bot_id}] 🔗 WebSocket连接已建立")

            # 启动后台接收线程（优先启动，避免漏掉CONNECTED消息）
            safe_print(f"[{self.bot_id}] [DEBUG] 启动后台 _recv_thread...")
            self._recv_thread = threading.Thread(
                target=self._recv_loop,
                name=f"ws-recv-{self.bot_id}",
                daemon=True
            )
            self._recv_thread.start()

            # 等待CONNECTED消息（后台线程会捕获并处理）
            safe_print(f"[{self.bot_id}] ⏳ 等待CONNECTED消息...")
            if not self._connected_event.wait(timeout=5):
                self._running = False
                safe_print(f"[{self.bot_id}] [DEBUG] 超时! player_id={self.player_id}")
                raise TimeoutError(f"[{self.bot_id}] 等待CONNECTED消息超时")
            safe_print(f"[{self.bot_id}] ✅ CONNECTED消息已处理, player_id={self.player_id}")

        except Exception as e:
            self.connected = False
            self._running = False
            safe_print(f"[{self.bot_id}] ❌ WebSocket连接失败: {e}")
            raise

    def _recv_loop(self):
        """后台接收消息循环
        原理：设置recv超时（1秒），定期检查 _running 标志
        这样既不会永久阻塞，也能及时响应消息
        """
        safe_print(f"[{self.bot_id}] [DEBUG] _recv_loop 线程已启动")
        # 设置超时，防止 recv() 永久阻塞
        try:
            self.ws.settimeout(1.0)
        except Exception as e:
            safe_print(f"[{self.bot_id}] [DEBUG] settimeout 失败: {e}")

        while self._running and self.ws:
            try:
                raw_message = self.ws.recv()
                if raw_message is None or raw_message == "":
                    safe_print(f"[{self.bot_id}] [DEBUG] recv() 返回空")
                    break
                safe_print(f"[{self.bot_id}] [DEBUG] recv() 收到消息，长度={len(raw_message)}")
                self._on_message(raw_message)
            except websocket.WebSocketTimeoutException:
                # 超时是正常的，继续循环检查 _running
                continue
            except websocket.WebSocketConnectionClosedException:
                safe_print(f"[{self.bot_id}] [DEBUG] WebSocketConnectionClosedException")
                break
            except Exception as e:
                if self._running:
                    safe_print(f"[{self.bot_id}] ⚠️ 接收消息异常: {type(e).__name__}: {e}")
                break
        safe_print(f"[{self.bot_id}] [DEBUG] _recv_loop 退出")
        self.connected = False
        safe_print(f"[{self.bot_id}] 🔌 WebSocket连接已关闭")

    def _on_message(self, raw_message: str):
        """收到原始消息"""
        safe_print(f"[{self.bot_id}] [DEBUG] _on_message 被调用, 消息={repr(raw_message[:100])}")
        self.message_handler.handle_message(raw_message)

        try:
            data = json.loads(raw_message)
            msg_type = data.get("type")
            payload = data.get("payload", {})
            safe_print(f"[{self.bot_id}] [DEBUG] 消息类型: {msg_type}")

            if msg_type == "CONNECTED":
                self.player_id = payload.get("playerId")
                self.room_id = payload.get("roomId")
                safe_print(f"[{self.bot_id}] [DEBUG] 收到CONNECTED! player_id={self.player_id}")
                if self._connected_event:
                    self._connected_event.set()

            if self.on_message_callback:
                self.on_message_callback(msg_type, payload)

        except Exception as e:
            safe_print(f"[{self.bot_id}] ⚠️ 消息处理异常: {e}")

    def send_message(self, msg_type: str, payload: Dict[str, Any], request_id: Optional[str] = None):
        """发送消息"""
        if not self.connected or not self.ws:
            safe_print(f"[{self.bot_id}] ⚠️ 未连接，无法发送消息")
            return

        if request_id is None:
            request_id = str(uuid.uuid4())

        message = {
            "type": msg_type,
            "payload": payload,
            "timestamp": int(time.time() * 1000),
            "requestId": request_id
        }

        try:
            self.ws.send(json.dumps(message, ensure_ascii=False))
            safe_print(f"[{self.bot_id}] 📤 发送消息: {msg_type} | RequestID: {request_id}")
        except Exception as e:
            safe_print(f"[{self.bot_id}] ❌ 发送消息失败: {e}")

    def join_room(self, room_id: str):
        payload = {"roomId": room_id}
        self.send_message("JOIN_ROOM", payload)

    def ready(self, is_ready: bool = True):
        payload = {"ready": is_ready}
        self.send_message("READY", payload)

    def send_game_action(self, action: str, phase: Optional[str] = None, target: Optional[int] = None, reason: Optional[str] = None):
        payload = {"action": action}
        if phase:
            payload["phase"] = phase
        if target is not None:
            payload["target"] = target
        if reason:
            payload["reason"] = reason
        self.send_message("GAME_ACTION", payload)

    def close(self):
        self._running = False
        if self.ws and self.connected:
            try:
                self.ws.close()
                safe_print(f"[{self.bot_id}] 🔌 主动关闭WebSocket连接")
            except:
                pass
        self.connected = False
