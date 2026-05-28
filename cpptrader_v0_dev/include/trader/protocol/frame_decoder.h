/*!
    \file frame_decoder.h
    \brief Frame decoder for handling sticky packet scenarios
    \author CppTrader Team
    \date 18.05.2026
    \copyright MIT License
*/

#ifndef CPPTRADER_PROTOCOL_FRAME_DECODER_H
#define CPPTRADER_PROTOCOL_FRAME_DECODER_H

#include "protocol.h"

#include <cstdint>
#include <vector>
#include <functional>
#include <optional>

namespace CppTrader {
namespace Protocol {

//! Frame decoder state
enum class DecodeState
{
    HEAD,
    BODY
};

//! Decoded frame
struct Frame
{
    //! Message header
    MsgHeader Header;
    //! Message body data
    std::vector<uint8_t> Body;

    Frame() noexcept = default;
    Frame(const MsgHeader& header, const std::vector<uint8_t>& body) noexcept
        : Header(header)
        , Body(body)
    {}
    Frame(const MsgHeader& header, std::vector<uint8_t>&& body) noexcept
        : Header(header)
        , Body(std::move(body))
    {}

    //! Get total frame size (header + body)
    size_t TotalSize() const noexcept { return sizeof(MsgHeader) + Body.size(); }

    //! Get pointer to header as bytes
    const uint8_t* HeaderBytes() const noexcept { return reinterpret_cast<const uint8_t*>(&Header); }

    //! Get pointer to body data
    const uint8_t* BodyBytes() const noexcept { return Body.data(); }
};

//! Frame decoder for handling TCP sticky packet / partial packet scenarios
/*!
    This class implements a state machine to decode protocol frames from
    a stream of bytes. It handles:
    - Multiple frames in a single read (sticky packets)
    - Single frame split across multiple reads (partial packets)
    - Invalid frames with error recovery
*/
class FrameDecoder
{
public:
    //! Frame decoder handler type
    using FrameHandler = std::function<void(const Frame&)>;
    //! Error handler type
    using ErrorHandler = std::function<void(const char* message, const uint8_t* data, size_t size)>;

    FrameDecoder();
    ~FrameDecoder() = default;

    FrameDecoder(const FrameDecoder&) = delete;
    FrameDecoder(FrameDecoder&&) = delete;
    FrameDecoder& operator=(const FrameDecoder&) = delete;
    FrameDecoder& operator=(FrameDecoder&&) = delete;

    //! Feed raw bytes into the decoder
    /*!
        \param data - Pointer to raw bytes
        \param size - Number of bytes
    */
    void Feed(const uint8_t* data, size_t size);

    //! Feed raw bytes from a vector
    /*!
        \param data - Vector of bytes
    */
    void Feed(const std::vector<uint8_t>& data);

    //! Try to decode a single complete frame from the internal buffer
    /*!
        \return Decoded frame if a complete frame is available, std::nullopt otherwise
    */
    std::optional<Frame> TryDecode();

    //! Process all complete frames in the buffer using the handler
    /*!
        \param handler - Callback function to process each decoded frame
        \return Number of frames processed
    */
    size_t ProcessFrames(const FrameHandler& handler);

    //! Set error handler
    void SetErrorHandler(const ErrorHandler& handler) { _error_handler = handler; }

    //! Get current decode state
    DecodeState State() const noexcept { return _state; }

    //! Get number of bytes in the internal buffer
    size_t BufferSize() const noexcept { return _buffer.size(); }

    //! Clear the internal buffer
    void Clear() noexcept;

    //! Get current header being parsed (valid only in BODY state)
    const MsgHeader& CurrentHeader() const noexcept { return _current_header; }

private:
    //! Internal buffer for incomplete data
    std::vector<uint8_t> _buffer;
    //! Current decode state
    DecodeState _state;
    //! Current header being parsed (valid in BODY state)
    MsgHeader _current_header;
    //! Last sequence number for monotonic validation
    uint32_t _last_sequence;
    //! Error handler
    ErrorHandler _error_handler;

    //! Try to parse header from buffer
    bool TryParseHeader() noexcept;

    //! Try to parse body from buffer
    std::optional<Frame> TryParseBody() noexcept;

    //! Discard bytes until we find a valid magic number
    void SyncToMagic() noexcept;

    //! Report error
    void ReportError(const char* message) noexcept;
};

} // namespace Protocol
} // namespace CppTrader

#endif // CPPTRADER_PROTOCOL_FRAME_DECODER_H
