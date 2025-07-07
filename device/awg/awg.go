package awg

import (
	"bytes"
	"fmt"
	"slices"
	"strconv"
	"strings"
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
	// InitPacketMagicHeader      uint32
	// ResponsePacketMagicHeader  uint32
	// UnderloadPacketMagicHeader uint32
	// TransportPacketMagicHeader uint32

	InitPacketMagicHeader      Limit
	ResponsePacketMagicHeader  Limit
	UnderloadPacketMagicHeader Limit
	TransportPacketMagicHeader Limit
}

type Limit struct {
	Min        uint32
	Max        uint32
	HeaderType uint32
}

func NewLimit(min, max, headerType uint32) (Limit, error) {
	if min > max {
		return Limit{}, fmt.Errorf("min (%d) cannot be greater than max (%d)", min, max)
	}

	return Limit{
		Min:        min,
		Max:        max,
		HeaderType: headerType,
	}, nil
}

func ParseMagicHeader(key, value string, defaultHeaderType uint32) (Limit, error) {
	splitLimits := strings.Split(value, "-")
	if len(splitLimits) != 2 {
		magicHeader, err := strconv.ParseUint(value, 10, 32)
		if err != nil {
			return Limit{}, fmt.Errorf("parse key: %s; value: %s; %w", key, value, err)
		}

		return NewLimit(uint32(magicHeader), uint32(magicHeader), defaultHeaderType)
	}

	min, err := strconv.ParseUint(splitLimits[0], 10, 32)
	if err != nil {
		return Limit{}, fmt.Errorf("parse min key: %s; value: %s; %w", key, splitLimits[0], err)
	}

	max, err := strconv.ParseUint(splitLimits[1], 10, 32)
	if err != nil {
		return Limit{}, fmt.Errorf("parse max key: %s; value: %s; %w", key, splitLimits[1], err)
	}

	limit, err := NewLimit(uint32(min), uint32(max), defaultHeaderType)
	if err != nil {
		return Limit{}, fmt.Errorf("new limit key: %s; value: %s-%s; %w", key, splitLimits[0], splitLimits[1], err)
	}

	return limit, nil
}

type Limits []Limit

func NewLimits(limits ...Limit) Limits {
	slices.SortFunc(limits, func(a, b Limit) int {
		if a.Min < b.Min {
			return -1
		} else if a.Min > b.Min {
			return 1
		}
		return 0
	})

	return Limits(limits)
}

type Protocol struct {
	IsASecOn abool.AtomicBool
	// TODO: revision the need of the mutex
	ASecMux     sync.RWMutex
	ASecCfg     aSecCfgType
	JunkCreator junkCreator

	HandshakeHandler SpecialHandshakeHandler

	limits Limits
}

func (protocol *Protocol) CreateInitHeaderJunk() ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.InitHeaderJunkSize, 0)
}

func (protocol *Protocol) CreateResponseHeaderJunk() ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.ResponseHeaderJunkSize, 0)
}

func (protocol *Protocol) CreateCookieReplyHeaderJunk() ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.CookieReplyHeaderJunkSize, 0)
}

func (protocol *Protocol) CreateTransportHeaderJunk(packetSize int) ([]byte, error) {
	return protocol.createHeaderJunk(protocol.ASecCfg.TransportHeaderJunkSize, packetSize)
}

func (protocol *Protocol) createHeaderJunk(junkSize int, extraSize int) ([]byte, error) {
	var junk []byte
	protocol.ASecMux.RLock()

	if junkSize != 0 {
		buf := make([]byte, 0, junkSize+extraSize)
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

func (protocol *Protocol) GetLimitMin(msgType uint32) (uint32, error) {
	for _, limit := range protocol.limits {
		if limit.Min <= msgType && msgType <= limit.Max {
			return limit.Min, nil
		}
	}

	return 0, fmt.Errorf("no limit found for message type: %d", msgType)
}
