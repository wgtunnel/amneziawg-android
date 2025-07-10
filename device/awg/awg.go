package awg

import (
	"bytes"
	"fmt"
	"sync"

	"github.com/tevino/abool"
)

type aSecCfgType struct {
	IsSet                     bool
	JunkPacketCount           int
	JunkPacketMinSize         int
	JunkPacketMaxSize         int
	InitHeaderJunkSize        int
	ResponseHeaderJunkSize    int
	CookieReplyHeaderJunkSize int
	TransportHeaderJunkSize   int

	InitPacketMagicHeader      MagicHeader
	ResponsePacketMagicHeader  MagicHeader
	UnderloadPacketMagicHeader MagicHeader
	TransportPacketMagicHeader MagicHeader
}

type Protocol struct {
	IsASecOn abool.AtomicBool
	// TODO: revision the need of the mutex
	ASecMux      sync.RWMutex
	ASecCfg      aSecCfgType
	JunkCreator  junkCreator
	MagicHeaders MagicHeaders

	HandshakeHandler SpecialHandshakeHandler
}

func (protocol *Protocol) CreateInitHeaderJunk() ([]byte, error) {
	protocol.ASecMux.RLock()
	defer protocol.ASecMux.RUnlock()

	return protocol.createHeaderJunk(protocol.ASecCfg.InitHeaderJunkSize, 0)
}

func (protocol *Protocol) CreateResponseHeaderJunk() ([]byte, error) {
	protocol.ASecMux.RLock()
	defer protocol.ASecMux.RUnlock()

	return protocol.createHeaderJunk(protocol.ASecCfg.ResponseHeaderJunkSize, 0)
}

func (protocol *Protocol) CreateCookieReplyHeaderJunk() ([]byte, error) {
	protocol.ASecMux.RLock()
	defer protocol.ASecMux.RUnlock()

	return protocol.createHeaderJunk(protocol.ASecCfg.CookieReplyHeaderJunkSize, 0)
}

func (protocol *Protocol) CreateTransportHeaderJunk(packetSize int) ([]byte, error) {
	protocol.ASecMux.RLock()
	defer protocol.ASecMux.RUnlock()

	return protocol.createHeaderJunk(protocol.ASecCfg.TransportHeaderJunkSize, packetSize)
}

func (protocol *Protocol) createHeaderJunk(junkSize int, extraSize int) ([]byte, error) {
	if junkSize == 0 {
		return nil, nil
	}

	var junk []byte
	buf := make([]byte, 0, junkSize+extraSize)
	writer := bytes.NewBuffer(buf[:0])

	err := protocol.JunkCreator.AppendJunk(writer, junkSize)
	if err != nil {
		return nil, fmt.Errorf("append junk: %w", err)
	}

	junk = writer.Bytes()

	return junk, nil
}

func (protocol *Protocol) GetMagicHeaderMinFor(msgTypeRange uint32) (uint32, error) {
	for _, limit := range protocol.MagicHeaders.headerValues {
		if limit.Min <= msgTypeRange && msgTypeRange <= limit.Max {
			return limit.Min, nil
		}
	}

	return 0, fmt.Errorf("no header for range: %d", msgTypeRange)
}

func (protocol *Protocol) GetMsgType(defaultMsgType uint32) (uint32, error) {
	return protocol.MagicHeaders.Get(defaultMsgType)
}
