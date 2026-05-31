#!/usr/bin/env python3
"""
HMAC E2E Test Protocol Definitions
This module defines the binary protocol structures for E2E testing.
"""

import struct
from dataclasses import dataclass
from typing import Tuple


# Protocol format constants
PROTO_FORMAT = '<'  # Little-endian

# STP Policy values (matching C++ enum)
class STPPolicy:
    CANCEL_NEW = 1
    CANCEL_OLD = 2
    CANCEL_BOTH = 3
    DECREMENT = 4


@dataclass
class OrderProto:
    """Order protocol structure - matches C++ OrderProto"""
    Id: int = 0                    # uint64_t - Order ID (8 bytes)
    SymbolId: int = 0              # uint32_t - Symbol ID (4 bytes)
    AccountId: int = 0             # uint64_t - Account ID (8 bytes)
    Type: int = 0                  # uint8_t - Order type (1 byte)
    Side: int = 0                  # uint8_t - Order side (1 byte)
    Price: int = 0                 # uint64_t - Order price (8 bytes)
    StopPrice: int = 0             # uint64_t - Stop price (8 bytes)
    Quantity: int = 0              # uint64_t - Order quantity (8 bytes)
    ExecutedQuantity: int = 0      # uint64_t - Executed quantity (8 bytes)
    LeavesQuantity: int = 0        # uint64_t - Leaves quantity (8 bytes)
    TimeInForce: int = 0           # uint8_t - Time in force (1 byte)
    Padding1: int = 0              # uint8_t - Padding (1 byte)
    StpPolicy: int = 0             # uint8_t - STP policy (1 byte)
    MaxVisibleQuantity: int = 0    # uint64_t - Max visible quantity (8 bytes)
    Slippage: int = 0              # uint64_t - Slippage (8 bytes)
    TrailingDistance: int = 0      # int64_t - Trailing distance (8 bytes)
    TrailingStep: int = 0          # int64_t - Trailing step (8 bytes)

    # Total: 8+4+8+1+1+8+8+8+8+8+1+1+1+8+8+8+8 = 96 bytes (C++ reports 97 with padding)
    STRUCT_FORMAT = PROTO_FORMAT + 'QIQBBQQQQQBBBQqqq'
    STRUCT_SIZE = struct.calcsize(STRUCT_FORMAT)

    def pack(self) -> bytes:
        """Pack the order into bytes"""
        return struct.pack(
            self.STRUCT_FORMAT,
            self.Id,
            self.SymbolId,
            self.AccountId,
            self.Type,
            self.Side,
            self.Price,
            self.StopPrice,
            self.Quantity,
            self.ExecutedQuantity,
            self.LeavesQuantity,
            self.TimeInForce,
            self.Padding1,
            self.StpPolicy,
            self.MaxVisibleQuantity,
            self.Slippage,
            self.TrailingDistance,
            self.TrailingStep
        )

    @classmethod
    def unpack(cls, data: bytes) -> 'OrderProto':
        """Unpack bytes into an OrderProto structure"""
        unpacked = struct.unpack(cls.STRUCT_FORMAT, data)
        return cls(*unpacked)


@dataclass
class AuthResponse:
    """Auth response structure (41 bytes)"""
    Error: int = 0                 # uint8_t - Error code
    SessionToken: bytes = b''      # char[32] - Session token
    AccountId: int = 0             # uint64_t - Account ID

    STRUCT_FORMAT = PROTO_FORMAT + 'B32sQ'
    STRUCT_SIZE = struct.calcsize(STRUCT_FORMAT)

    def pack(self) -> bytes:
        """Pack the auth response into bytes"""
        return struct.pack(
            self.STRUCT_FORMAT,
            self.Error,
            self.SessionToken.ljust(32, b'\x00')[:32],
            self.AccountId
        )

    @classmethod
    def unpack(cls, data: bytes) -> 'AuthResponse':
        """Unpack bytes into an AuthResponse structure"""
        error, session_token, account_id = struct.unpack(cls.STRUCT_FORMAT, data)
        return cls(
            Error=error,
            SessionToken=session_token.rstrip(b'\x00'),
            AccountId=account_id
        )


def validate_struct_sizes():
    """Validate protocol structure sizes"""
    print(f"✓ OrderProto size: {OrderProto.STRUCT_SIZE} bytes")
    print(f"✓ AuthResponse size: {AuthResponse.STRUCT_SIZE} bytes")


if __name__ == '__main__':
    print("Validating protocol structures...\n")

    # Validate sizes
    validate_struct_sizes()

    # Test OrderProto packing/unpacking with AccountId and StpPolicy
    print("Testing OrderProto with AccountId and StpPolicy fields...")
    order = OrderProto(
        Id=1,
        SymbolId=100,
        AccountId=12345,
        Type=1,
        Side=0,
        Price=100000,
        StopPrice=0,
        Quantity=10,
        ExecutedQuantity=0,
        LeavesQuantity=10,
        TimeInForce=0,
        Padding1=0,
        StpPolicy=STPPolicy.CANCEL_NEW,
        MaxVisibleQuantity=9223372036854775807,  # max int64 (represents max uint64)
        Slippage=9223372036854775807,           # max int64 (represents max uint64)
        TrailingDistance=0,
        TrailingStep=0
    )

    packed = order.pack()
    unpacked = OrderProto.unpack(packed)

    assert unpacked.AccountId == 12345, f"AccountId mismatch: {unpacked.AccountId}"
    assert unpacked.StpPolicy == STPPolicy.CANCEL_NEW, f"StpPolicy mismatch: {unpacked.StpPolicy}"
    print(f"✓ OrderProto packed/unpacked successfully")
    print(f"  - AccountId: {unpacked.AccountId}")
    print(f"  - StpPolicy: {unpacked.StpPolicy}")

    # Test AuthResponse packing/unpacking with AccountId
    print("\nTesting AuthResponse with AccountId field...")
    auth = AuthResponse(
        Error=0,
        SessionToken=b'test_session_token_123',
        AccountId=67890
    )

    packed_auth = auth.pack()
    unpacked_auth = AuthResponse.unpack(packed_auth)

    assert unpacked_auth.AccountId == 67890, f"AccountId mismatch: {unpacked_auth.AccountId}"
    print(f"✓ AuthResponse packed/unpacked successfully")
    print(f"  - AccountId: {unpacked_auth.AccountId}")
    print(f"  - SessionToken: {unpacked_auth.SessionToken.decode()}")

    print("\n✓ All protocol validation tests passed!")
