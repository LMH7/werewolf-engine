"""
HTTP API模块 - D1-D2阶段
封装与后端的HTTP交互
"""
import requests
from typing import Dict, Any, Optional
from config import BotConfig, default_config


class HttpApiClient:
    """HTTP API客户端"""
    
    def __init__(self, config: BotConfig = default_config):
        self.config = config
        self.session = requests.Session()
    
    def create_room(self, ai_count: int = 11, token: Optional[str] = None) -> Dict[str, Any]:
        """
        创建房间 - PRD §6.1 POST /api/room
        
        Args:
            ai_count: AI玩家数量（0-12）
            token: 认证token（可选，用于Authorization header）
            
        Returns:
            响应JSON: {"roomId": "r_abc123", "maxPlayers": 12, ...}
        """
        url = self.config.get_http_url("/api/room")
        headers = {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        
        payload = {"aiCount": ai_count}
        
        response = self.session.post(url, json=payload, headers=headers)
        response.raise_for_status()
        return response.json()
    
    def join_room(self, room_id: str, token: str) -> Dict[str, Any]:
        """
        加入房间 - PRD §6.1 POST /api/room/{roomId}/join
        
        Args:
            room_id: 房间ID
            token: 认证token
            
        Returns:
            响应JSON: {"roomId": "...", "playerId": 3, "userId": 10001}
        """
        url = self.config.get_http_url(f"/api/room/{room_id}/join")
        headers = {"Authorization": f"Bearer {token}"}
        
        response = self.session.post(url, headers=headers)
        response.raise_for_status()
        return response.json()
    
    def ready(self, room_id: str, is_ready: bool, token: str) -> Dict[str, Any]:
        """
        准备/取消准备 - PRD §6.1 POST /api/room/{roomId}/ready
        
        Args:
            room_id: 房间ID
            is_ready: 是否准备
            token: 认证token
            
        Returns:
            响应JSON
        """
        url = self.config.get_http_url(f"/api/room/{room_id}/ready")
        headers = {"Authorization": f"Bearer {token}"}
        payload = {"ready": is_ready}
        
        response = self.session.post(url, json=payload, headers=headers)
        response.raise_for_status()
        return response.json()
    
    def start_game(self, room_id: str, token: str) -> Dict[str, Any]:
        """
        开始游戏 - PRD §6.1 POST /api/room/{roomId}/start
        
        Args:
            room_id: 房间ID
            token: 认证token（房主）
            
        Returns:
            响应JSON: {"started": true, "roomId": "..."}
        """
        url = self.config.get_http_url(f"/api/room/{room_id}/start")
        headers = {"Authorization": f"Bearer {token}"}
        
        response = self.session.post(url, headers=headers)
        response.raise_for_status()
        return response.json()
