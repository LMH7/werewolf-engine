"""
Bot玩家 - D1-D2阶段
整合HTTP API和WebSocket客户端
"""
import time
from typing import Optional
from config import BotConfig, default_config
from http_api import HttpApiClient
from ws_client import WebSocketClient
from message_handler import safe_print


class BotPlayer:
    """Bot玩家 - 封装完整的玩家行为"""
    
    def __init__(self, user_id: int, config: BotConfig = default_config):
        self.user_id = user_id
        self.config = config
        self.token = config.get_token(user_id)
        self.bot_id = f"Bot-{user_id}"
        
        # HTTP和WebSocket客户端
        self.http_client = HttpApiClient(config)
        self.ws_client: Optional[WebSocketClient] = None
        
        # 房间信息
        self.room_id: Optional[str] = None
        self.player_id: Optional[int] = None
        self.is_host = False
    
    def create_room(self, ai_count: int = 11) -> str:
        """
        创建房间（成为房主）
        
        Args:
            ai_count: AI玩家数量
            
        Returns:
            房间ID
        """
        safe_print(f"\n[{self.bot_id}] 📝 创建房间 (aiCount={ai_count})...")
        response = self.http_client.create_room(ai_count, self.token)
        self.room_id = response["roomId"]
        self.is_host = True
        safe_print(f"[{self.bot_id}] ✅ 房间创建成功: {self.room_id}")
        return self.room_id
    
    def connect_websocket(self):
        """建立WebSocket连接（connect()内部已等待CONNECTED消息确认）"""
        safe_print(f"\n[{self.bot_id}] 🔌 建立WebSocket连接...")
        self.ws_client = WebSocketClient(self.token, self.config, self.bot_id)
        self.ws_client.connect()
        # 注：ws_client.connect() 内部已通过事件机制等待CONNECTED确认，
        # 无需额外 sleep。player_id/room_id 此时已可用。
    
    def join_room(self, room_id: str):
        """
        加入房间
        
        Args:
            room_id: 房间ID
        """
        if not self.ws_client or not self.ws_client.connected:
            raise RuntimeError(f"[{self.bot_id}] 必须先建立WebSocket连接")
        
        safe_print(f"\n[{self.bot_id}] 🚪 加入房间: {room_id}...")
        self.room_id = room_id
        
        # Step 1: 先通过HTTP API加入房间，获取playerId
        response = self.http_client.join_room(room_id, self.token)
        self.player_id = response.get("playerId")
        safe_print(f"[{self.bot_id}] ✅ HTTP加入成功，playerId={self.player_id}")
        
        # Step 2: 通过WebSocket加入房间，接收实时消息
        self.ws_client.join_room(room_id)
        time.sleep(0.5)  # 等待PHASE_SYNC消息
        
        safe_print(f"[{self.bot_id}] ✅ 已加入房间，playerId={self.player_id}")
    
    def ready(self, is_ready: bool = True):
        """
        准备/取消准备
        
        Args:
            is_ready: 是否准备
        """
        if not self.ws_client:
            raise RuntimeError(f"[{self.bot_id}] 必须先建立WebSocket连接")
        
        status = "准备" if is_ready else "取消准备"
        safe_print(f"\n[{self.bot_id}] ✋ {status}...")
        self.ws_client.ready(is_ready)
        time.sleep(0.3)
    
    def start_game(self):
        """
        开始游戏（仅房主）
        """
        if not self.is_host:
            raise RuntimeError(f"[{self.bot_id}] 只有房主可以开始游戏")
        
        if not self.room_id:
            raise RuntimeError(f"[{self.bot_id}] 未加入房间")
        
        safe_print(f"\n[{self.bot_id}] 🎮 开始游戏...")
        self.http_client.start_game(self.room_id, self.token)
        time.sleep(0.5)  # 等待阶段切换消息
    
    def send_game_action(
        self,
        action: str,
        phase: Optional[str] = None,
        target: Optional[int] = None,
        reason: Optional[str] = None
    ):
        """
        发送游戏动作
        
        Args:
            action: 动作类型
            phase: 当前阶段
            target: 目标玩家
            reason: 操作理由
        """
        if not self.ws_client:
            raise RuntimeError(f"[{self.bot_id}] 必须先建立WebSocket连接")
        
        safe_print(f"\n[{self.bot_id}] 🎯 发送游戏动作: {action}")
        self.ws_client.send_game_action(action, phase, target, reason)
        time.sleep(0.3)
    
    def wait(self, seconds: float):
        """
        等待指定时间（用于观察消息）
        
        Args:
            seconds: 等待秒数
        """
        safe_print(f"\n[{self.bot_id}] ⏳ 等待 {seconds}秒...")
        time.sleep(seconds)
    
    def close(self):
        """关闭连接"""
        if self.ws_client:
            self.ws_client.close()
        safe_print(f"[{self.bot_id}] 👋 已断开连接")
