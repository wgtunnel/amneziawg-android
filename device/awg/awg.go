package awg

import (
	"bytes"
	"sync"

	"github.com/tevino/abool"
)

type aSecCfgType struct {
	IsSet                      bool
	JunkPacketCount            int
	JunkPacketMinSize          int
	JunkPacketMaxSize          int
	InitHeaderJunkSize         int
	ResponseHeaderJunkSize     int
	CookieReplyHeaderJunkSize  int
	TransportHeaderJunkSize    int
	InitPacketMagicHeader      uint32
	ResponsePacketMagicHeader  uint32
	UnderloadPacketMagicHeader uint32
	TransportPacketMagicHeader uint32
}

type Protocol struct {
	IsASecOn abool.AtomicBool
	// TODO: revision the need of the mutex
	ASecMux     sync.RWMutex
	ASecCfg     aSecCfgType
	JunkCreator junkCreator

	HandshakeHandler SpecialHandshakeHandler
}

func (protocol *Protocol) CreateInitHeaderJunk() ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.InitHeaderJunkSize)
}

func (protocol *Protocol) CreateResponseHeaderJunk() ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.ResponseHeaderJunkSize)
}

func (protocol *Protocol) CreateCookieReplyHeaderJunk() ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.CookieReplyHeaderJunkSize)
}

func (protocol *Protocol) CreateTransportHeaderJunk() ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.TransportHeaderJunkSize)
}

func (protocol *Protocol) createHeaderJunk(junkSize int) ([]byte, error) {
	var junk []byte
	protocol.ASecMux.RLock()
	if junkSize != 0 {
		buf := make([]byte, 0, junkSize)
		writer := bytes.NewBuffer(buf[:0])
		err := protocol.JunkCreator.AppendJunk(writer, junkSize)
		if err != nil {
			protocol.ASecMux.RUnlock()
			return nil, err
		}
		junk = writer.Bytes()
	}
	protocol.ASecMux.RUnlock()

	return junk, nil
}
