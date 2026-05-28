/*!
    \file frame_decoder.cpp
    \brief Frame decoder implementation
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#include "trader/protocol/frame_decoder.h"

#include <cstring>
#include <algorithm>
#include <iostream>

namespace CppTrader {
namespace Protocol {

FrameDecoder::FrameDecoder()
    : _state(DecodeState::HEAD)
    , _current_header()
    , _last_sequence(0)
{
}

void FrameDecoder::Feed(const uint8_t* data, size_t size)
{
    if (data == nullptr || size == 0)
        return;

    _buffer.insert(_buffer.end(), data, data + size);
}

void FrameDecoder::Feed(const std::vector<uint8_t>& data)
{
    if (data.empty())
        return;

    _buffer.insert(_buffer.end(), data.begin(), data.end());
}

std::optional<Frame> FrameDecoder::TryDecode()
{
    while (true)
    {
        switch (_state)
        {
            case DecodeState::HEAD:
            {
                if (!TryParseHeader())
                    return std::nullopt;

                // Validate header - separate magic and version checks for better diagnostics
                if (!_current_header.HasValidMagic())
                {
                    std::cout << "[DEBUG] FrameDecoder: Invalid header magic: got=0x" << std::hex << _current_header.Magic
                              << " expected=0x" << PROTOCOL_MAGIC << std::dec
                              << " version=" << (int)_current_header.Version
                              << " type=0x" << std::hex << static_cast<int>(_current_header.Type)
                              << " flags=0x" << static_cast<int>(_current_header.Flags)
                              << " length=" << std::dec << _current_header.Length << std::endl;
                    ReportError("Invalid header magic");
                    SyncToMagic();
                    continue;
                }

                if (!_current_header.HasValidVersion())
                {
                    std::cout << "[DEBUG] FrameDecoder: Invalid header version: got=" << std::dec << (int)_current_header.Version
                              << " expected=" << (int)PROTOCOL_VERSION
                              << " type=0x" << std::hex << static_cast<int>(_current_header.Type)
                              << " flags=0x" << static_cast<int>(_current_header.Flags)
                              << " length=" << std::dec << _current_header.Length << std::endl;
                    ReportError("Invalid header version");
                    SyncToMagic();
                    continue;
                }

                // Validate sequence number (monotonically increasing, 0 means not tracked)
                if (_current_header.Sequence != 0 && _current_header.Sequence <= _last_sequence)
                {
                    std::cout << "[DEBUG] FrameDecoder: Sequence rollback: got=" << _current_header.Sequence
                              << " last=" << _last_sequence << std::endl;
                    ReportError("Sequence number rollback detected");
                    SyncToMagic();
                    continue;
                }
                if (_current_header.Sequence != 0)
                {
                    _last_sequence = _current_header.Sequence;
                }

                // Validate body length (sanity check: max 64MB)
                if (_current_header.Length > 64 * 1024 * 1024)
                {
                    std::cout << "[DEBUG] FrameDecoder: body length exceeds max size=" << _current_header.Length << std::endl;
                    ReportError("Body length exceeds maximum allowed size");
                    SyncToMagic();
                    continue;
                }

                _state = DecodeState::BODY;
                break;
            }

            case DecodeState::BODY:
            {
                auto frame = TryParseBody();
                if (frame.has_value())
                {
                    _state = DecodeState::HEAD;
                    return frame;
                }
                return std::nullopt;
            }
        }
    }
}

size_t FrameDecoder::ProcessFrames(const FrameHandler& handler)
{
    size_t count = 0;

    if (!handler)
        return count;

    while (true)
    {
        auto frame = TryDecode();
        if (!frame.has_value())
            break;

        handler(frame.value());
        ++count;
    }

    return count;
}

void FrameDecoder::Clear() noexcept
{
    _buffer.clear();
    _buffer.shrink_to_fit();
    _state = DecodeState::HEAD;
    _current_header = MsgHeader();
    _last_sequence = 0;
}

bool FrameDecoder::TryParseHeader() noexcept
{
    if (_buffer.size() < sizeof(MsgHeader))
        return false;

    // Copy header from buffer (handle potential alignment issues)
    std::memcpy(&_current_header, _buffer.data(), sizeof(MsgHeader));

    // Remove header bytes from buffer
    _buffer.erase(_buffer.begin(), _buffer.begin() + sizeof(MsgHeader));

    return true;
}

std::optional<Frame> FrameDecoder::TryParseBody() noexcept
{
    const uint16_t body_length = _current_header.Length;

    if (_buffer.size() < body_length)
        return std::nullopt;

    // Extract body bytes
    std::vector<uint8_t> body(_buffer.begin(), _buffer.begin() + body_length);

    // Remove body bytes from buffer
    _buffer.erase(_buffer.begin(), _buffer.begin() + body_length);

    return Frame(_current_header, std::move(body));
}

void FrameDecoder::SyncToMagic() noexcept
{
    // Discard bytes until we find the magic number or run out of data
    while (_buffer.size() >= 2)
    {
        // Check for magic number (little-endian: 0x52, 0x54)
        if (_buffer[0] == 0x52 && _buffer[1] == 0x54)
        {
            // Found potential magic, but need at least sizeof(MsgHeader) to validate
            return;
        }

        // Discard first byte and continue searching
        _buffer.erase(_buffer.begin());
    }

    // Less than 2 bytes, can't have magic number
    if (_buffer.size() < 2)
        _buffer.clear();
}

void FrameDecoder::ReportError(const char* message) noexcept
{
    std::cout << "[DEBUG] FrameDecoder::ReportError: " << message << " buffer_size=" << _buffer.size() << std::endl;
    if (_error_handler)
    {
        _error_handler(message, _buffer.data(), _buffer.size());
    }
}

} // namespace Protocol
} // namespace CppTrader
