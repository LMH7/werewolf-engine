"""
消息处理器 - D1-D2阶段
解析和打印服务端消息，强制显示 requestId
使用线程锁保证多线程打印控制台不乱码
"""
import json
import threading
import sys
from datetime import datetime
from typing import Dict, Any, Optional


_print_lock = threading.Lock()
# 检测实际终端编码（Windows通常是gbk）
_CONSOLE_ENCODING = sys.stdout.encoding or 'utf-8'


def safe_print(*args, **kwargs):
    """
    线程安全的print
    确保同一时刻只有一个线程能往控制台写内容，避免"串标"
    兼容Windows GBK终端（自动将emoji等不可编码字符替换为?）
    """
    with _print_lock:
        try:
            print(*args, **kwargs)
        except UnicodeEncodeError:
            # 用实际终端编码encode → 自动丢弃不可编码字符 → decode回str再打印
            sanitized = []
            for a in args:
                if isinstance(a, str):
                    sanitized.append(a.encode(_CONSOLE_ENCODING, errors='replace')
                                      .decode(_CONSOLE_ENCODING, errors='replace'))
                else:
                    sanitized.append(a)
            print(*sanitized, **kwargs)


class MessageHandler:
    """消息处理器 - 负责解析和打印服务端消息"""

    def __init__(self, bot_id: str = "Bot"):
        self.bot_id = bot_id
        self.message_count = 0

    def parse_message(self, raw_message: str) -> Dict[str, Any]:
        """
        解析JSON消息

        Args:
            raw_message: 原始JSON字符串

        Returns:
            解析后的字典
        """
        try:
            return json.loads(raw_message)
        except json.JSONDecodeError as e:
            safe_print(f"[{self.bot_id}] ❌ JSON解析失败: {e}")
            safe_print(f"  原始消息: {raw_message}")
            return {}

    def log_message(self, msg_type: str, payload: Dict[str, Any], request_id: Optional[str] = None):
        """
        打印消息日志 - 强制显示 requestId（用户要求）

        Args:
            msg_type: 消息类型
            payload: 消息载荷
            request_id: 请求ID（必须显眼打印）
        """
        self.message_count += 1
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]

        # 分隔线
        safe_print("=" * 100)

        # 消息头 - requestId 使用特殊标记显眼显示
        if request_id:
            safe_print(f"[{timestamp}] [{self.bot_id}] 📨 消息#{self.message_count} | 类型: {msg_type}")
            safe_print(f"  🔑 RequestID: >>>>>> {request_id} <<<<<<")
        else:
            safe_print(f"[{timestamp}] [{self.bot_id}] 📨 消息#{self.message_count} | 类型: {msg_type}")
            safe_print(f"  🔑 RequestID: >>>>>> ⚠️  NONE  ⚠️  <<<<<<")

        # 载荷内容
        safe_print(f"  📦 Payload:")
        payload_str = json.dumps(payload, indent=4, ensure_ascii=False)
        for line in payload_str.split('\n'):
            safe_print(f"    {line}")

        safe_print("=" * 100)
        safe_print()  # 空行分隔

    def handle_message(self, raw_message: str):
        """
        处理接收到的消息 - PRD §4.6.2 信封格式

        消息格式:
        {
            "type": "MESSAGE_TYPE",
            "payload": {},
            "timestamp": 1715760000000,
            "requestId": "optional-uuid"
        }

        Args:
            raw_message: 原始JSON字符串
        """
        message = self.parse_message(raw_message)
        if not message:
            return

        msg_type = message.get("type", "UNKNOWN")
        payload = message.get("payload", {})
        request_id = message.get("requestId")
        server_timestamp = message.get("timestamp")

        # 打印消息（强制显示requestId）
        self.log_message(msg_type, payload, request_id)

        # 根据消息类型进行特殊处理（D1-D2阶段主要是打印）
        if msg_type == "CONNECTED":
            self._handle_connected(payload)
        elif msg_type == "PHASE_SYNC":
            self._handle_phase_sync(payload)
        elif msg_type == "ACTION_ACK":
            self._handle_action_ack(payload)
        elif msg_type == "ERROR":
            self._handle_error(payload)

    def _handle_connected(self, payload: Dict[str, Any]):
        """处理 CONNECTED 消息"""
        player_id = payload.get("playerId")
        room_id = payload.get("roomId")
        user_id = payload.get("userId")
        safe_print(f"  ✅ 连接成功! playerId={player_id}, roomId={room_id}, userId={user_id}")
        safe_print()

    def _handle_phase_sync(self, payload: Dict[str, Any]):
        """处理 PHASE_SYNC 消息 - PRD §4.6.4"""
        current_phase = payload.get("currentPhase")
        round_num = payload.get("round")
        countdown = payload.get("countdown")
        alive_players = payload.get("alivePlayers", [])
        your_role = payload.get("yourRole")

        safe_print(f"  🎮 阶段同步: {current_phase} (第{round_num}轮)")
        safe_print(f"     倒计时: {countdown}s | 存活玩家: {alive_players}")
        safe_print(f"     你的角色: {your_role}")
        safe_print()

    def _handle_action_ack(self, payload: Dict[str, Any]):
        """处理 ACTION_ACK 消息 - PRD §4.6.4"""
        success = payload.get("success")
        message = payload.get("message")
        code = payload.get("code")
        server_phase = payload.get("serverPhase")

        status = "✅ 成功" if success else "❌ 失败"
        safe_print(f"  {status} 操作响应: {message}")
        if code:
            safe_print(f"     错误码: {code}")
        safe_print(f"     服务端阶段: {server_phase}")
        safe_print()

    def _handle_error(self, payload: Dict[str, Any]):
        """处理 ERROR 消息"""
        error_msg = payload.get("message", "未知错误")
        error_code = payload.get("code", "UNKNOWN")
        safe_print(f"  ❌ 错误: {error_msg} (code: {error_code})")
        safe_print()
