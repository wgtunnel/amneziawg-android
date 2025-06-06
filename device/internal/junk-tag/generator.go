package junktag

import (
	crand "crypto/rand"
	"encoding/hex"
	"fmt"
	"strconv"
	"strings"
	"time"

	v2 "math/rand/v2"
)

type Generator interface {
	Generate() ([]byte, error)
}

type newGenerator func(string) (Generator, error)

type BytesGenerator struct {
	value []byte
}

func (bg *BytesGenerator) Generate() ([]byte, error) {
	return bg.value, nil
}

func newBytesGenerator(param string) (Generator, error) {
	isNotHex := !strings.HasPrefix(param, "0x") ||
		!strings.HasPrefix(param, "0x") && !isHexString(param)
	if isNotHex {
		return nil, fmt.Errorf("not correct hex: %s", param)
	}

	hex, err := hexToBytes(param)
	if err != nil {
		return nil, fmt.Errorf("hexToBytes: %w", err)
	}

	return &BytesGenerator{value: hex}, nil
}

func isHexString(s string) bool {
	for _, char := range s {
		if !((char >= '0' && char <= '9') ||
			(char >= 'a' && char <= 'f') ||
			(char >= 'A' && char <= 'F')) {
			return false
		}
	}
	return len(s) > 0
}

func hexToBytes(hexStr string) ([]byte, error) {
	hexStr = strings.TrimPrefix(hexStr, "0x")
	hexStr = strings.TrimPrefix(hexStr, "0X")

	// Ensure even length (pad with leading zero if needed)
	if len(hexStr)%2 != 0 {
		hexStr = "0" + hexStr
	}

	return hex.DecodeString(hexStr)
}

type RandomPacketGenerator struct {
	cha8Rand *v2.ChaCha8
	size     int
}

func (rpg *RandomPacketGenerator) Generate() ([]byte, error) {
	junk := make([]byte, rpg.size)
	_, err := rpg.cha8Rand.Read(junk)
	return junk, err
}

func newRandomPacketGenerator(param string) (Generator, error) {
	size, err := strconv.Atoi(param)
	if err != nil {
		return nil, fmt.Errorf("randome packet parse int: %w", err)
	}
	// TODO: add size check

	buf := make([]byte, 32)
	_, err = crand.Read(buf)
	if err != nil {
		return nil, fmt.Errorf("randome packet crand read: %w", err)
	}

	return &RandomPacketGenerator{cha8Rand: v2.NewChaCha8([32]byte(buf)), size: size}, nil
}

type TimestampGenerator struct {
}

func (tg *TimestampGenerator) Generate() ([]byte, error) {
	return time.Now().MarshalBinary()
}

func newTimestampGenerator(param string) (Generator, error) {
	if len(param) != 0 {
		return nil, fmt.Errorf("timestamp param needs to be empty: %s", param)
	}

	return &TimestampGenerator{}, nil
}
