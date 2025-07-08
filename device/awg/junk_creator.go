package awg

import (
	"bytes"
	"fmt"
)

type junkCreator struct {
	aSecCfg         aSecCfgType
	randomGenerator PRNG[int]
}

// TODO: refactor param to only pass the junk related params
func NewJunkCreator(aSecCfg aSecCfgType) junkCreator {
	return junkCreator{aSecCfg: aSecCfg, randomGenerator: NewPRNG[int]()}
}

// Should be called with aSecMux RLocked
func (jc *junkCreator) CreateJunkPackets(junks *[][]byte) {
	if jc.aSecCfg.JunkPacketCount == 0 {
		return
	}

	for range jc.aSecCfg.JunkPacketCount {
		packetSize := jc.randomPacketSize()
		junk := jc.randomJunkWithSize(packetSize)
		*junks = append(*junks, junk)
	}
	return
}

// Should be called with aSecMux RLocked
func (jc *junkCreator) randomPacketSize() int {
	return jc.randomGenerator.RandomSizeInRange(jc.aSecCfg.JunkPacketMinSize, jc.aSecCfg.JunkPacketMaxSize)
}

// Should be called with aSecMux RLocked
func (jc *junkCreator) AppendJunk(writer *bytes.Buffer, size int) error {
	headerJunk := jc.randomJunkWithSize(size)
	_, err := writer.Write(headerJunk)
	if err != nil {
		return fmt.Errorf("write header junk: %v", err)
	}
	return nil
}

// Should be called with aSecMux RLocked
func (jc *junkCreator) randomJunkWithSize(size int) []byte {
	return jc.randomGenerator.ReadSize(size)
}
