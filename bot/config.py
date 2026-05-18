"""
配置模块 - D1-D2阶段
根据PRD §4.2.4，直接使用 userId 作为 token
"""
import os
from typing import Optional


class BotConfig:
    """Bot配置类"""

    def __init__(
        self,
        base_url: str = "http://localhost:8090",
        ws_url: str = "ws://localhost:8090"
    ):
        self.base_url = base_url
        self.ws_url = ws_url

        # 从环境变量读取（可选）
        self.base_url = os.getenv("WEREWOLF_BASE_URL", self.base_url)
        self.ws_url = os.getenv("WEREWOLF_WS_URL", self.ws_url)

    def get_token(self, user_id: int) -> str:
        """
        根据PRD §4.2.4，MVP阶段直接使用 userId 作为 token

        Args:
            user_id: 用户ID

        Returns:
            token字符串
        """
        return str(user_id)

    def get_http_url(self, path: str) -> str:
        """
        构造HTTP API URL

        Args:
            path: API路径（如 /api/room）

        Returns:
            完整的HTTP URL
        """
        return f"{self.base_url}{path}"


# 默认配置实例
default_config = BotConfig()
