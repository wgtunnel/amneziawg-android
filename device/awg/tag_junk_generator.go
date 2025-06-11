package awg

import (
	"fmt"
	"strconv"
)

type TagJunkGenerator struct {
	name       string
	packetSize int
	generators []Generator
}

func newTagJunkGenerator(name string, size int) TagJunkGenerator {
	return TagJunkGenerator{name: name, generators: make([]Generator, 0, size)}
}

func (tg *TagJunkGenerator) append(generator Generator) {
	tg.generators = append(tg.generators, generator)
	tg.packetSize += generator.Size()
}

func (tg *TagJunkGenerator) generatePacket() []byte {
	packet := make([]byte, 0, tg.packetSize)
	for _, generator := range tg.generators {
		packet = append(packet, generator.Generate()...)
	}

	return packet
}

func (tg *TagJunkGenerator) Name() string {
	return tg.name
}

func (tg *TagJunkGenerator) nameIndex() (int, error) {
	if len(tg.name) != 2 {
		return 0, fmt.Errorf("name must be 2 character long: %s", tg.name)
	}

	index, err := strconv.Atoi(tg.name[1:2])
	if err != nil {
		return 0, fmt.Errorf("name 2 char should be an int %w", err)
	}
	return index, nil
}
