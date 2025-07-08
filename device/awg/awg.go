package awg

import (
	"bytes"
	"fmt"
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
	Min uint32
	Max uint32
}

func NewLimitSameValue(value uint32) Limit {
	return Limit{
		Min: value,
		Max: value,
	}
}

func NewLimit(min, max uint32) (Limit, error) {
	if min > max {
		return Limit{}, fmt.Errorf("min (%d) cannot be greater than max (%d)", min, max)
	}

	return Limit{
		Min: min,
		Max: max,
	}, nil
}

func ParseMagicHeader(key, value string) (Limit, error) {
	splitLimits := strings.Split(value, "-")
	if len(splitLimits) != 2 {
		magicHeader, err := strconv.ParseUint(value, 10, 32)
		if err != nil {
			return Limit{}, fmt.Errorf("parse key: %s; value: %s; %w", key, value, err)
		}

		return NewLimit(uint32(magicHeader), uint32(magicHeader))
	}

	min, err := strconv.ParseUint(splitLimits[0], 10, 32)
	if err != nil {
		return Limit{}, fmt.Errorf("parse min key: %s; value: %s; %w", key, splitLimits[0], err)
	}

	max, err := strconv.ParseUint(splitLimits[1], 10, 32)
	if err != nil {
		return Limit{}, fmt.Errorf("parse max key: %s; value: %s; %w", key, splitLimits[1], err)
	}

	limit, err := NewLimit(uint32(min), uint32(max))
	if err != nil {
		return Limit{}, fmt.Errorf("new limit key: %s; value: %s-%s; %w", key, splitLimits[0], splitLimits[1], err)
	}

	return limit, nil
}

type Limits struct {
	Limits          []Limit
	randomGenerator PRNG[uint32]
}

func NewLimits(limits []Limit) Limits {
	// TODO: check if limits doesn't overlap
	return Limits{Limits: limits, randomGenerator: NewPRNG[uint32]()}
}

func (l *Limits) Get(defaultMsgType uint32) (uint32, error) {
	if defaultMsgType == 0 || defaultMsgType > 4 {
		return 0, fmt.Errorf("invalid message type: %d", defaultMsgType)
	}

	return l.randomGenerator.RandomSizeInRange(l.Limits[defaultMsgType-1].Min, l.Limits[defaultMsgType-1].Max), nil
}

type Protocol struct {
	IsASecOn abool.AtomicBool
	// TODO: revision the need of the mutex
	ASecMux     sync.RWMutex
	ASecCfg     aSecCfgType
	JunkCreator junkCreator

	HandshakeHandler SpecialHandshakeHandler

	MagicHeaders Limits
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
	fmt.Println(protocol.MagicHeaders.Limits)
	for _, limit := range protocol.MagicHeaders.Limits {
		if limit.Min <= msgType && msgType <= limit.Max {
			return limit.Min, nil
		}
	}

	return 0, fmt.Errorf("no limit found for message type: %d", msgType)
}

func (protocol *Protocol) Get(defaultMsgType uint32) (uint32, error) {
	return protocol.MagicHeaders.Get(defaultMsgType)
}
